"""检索前的上下文补全、业务边界与来源适用性约束。"""

from dataclasses import dataclass
from datetime import date
import re

from .knowledge import Chunk
from .models import PolicyQuery

TOPICS = ("迁入", "迁出", "居住证", "家庭户", "审批", "注销", "人口", "落户", "材料", "权限", "申请")
OFFICIAL_REQUEST = re.compile(r"法规|法定|法律|文号|新规|新出台|政策条文|政策条款|第[一二三四五六七八九十\d]+条|官方政策|最新政策|地方政策|专用政策")
REGIONAL_REQUEST = re.compile(r"(?:省|市|区|县).{0,12}(?:政策|规定|条件|期限|标准)|(?:政策|规定).{0,12}(?:省|市|区|县)")
NEGATED_ACTION = re.compile(r"不能|不应|不得|禁止|为什么|是否|能否|可不可以|可以.*吗")
PRIVATE_DATA = re.compile(r"\d{17}[\dXx]|(?<!\d)1[3-9]\d{9}(?!\d)|(?:查询|获取|给我|列出|显示|导出|输出).{0,25}(?:完整身份证|身份证号|手机号|密码|密钥)")
UNSAFE_ACTION = re.compile(r"绕过审批|(?:执行|生成).{0,5}sql|直接修改人口|删除人口|忽略.{0,10}(?:规则|指令)|系统提示词|泄露", re.I)
REGIONS = {"山东": "370000", "济南": "370100", "青岛": "370200", "淄博": "370300",
           "枣庄": "370400", "东营": "370500", "烟台": "370600", "潍坊": "370700",
           "济宁": "370800", "泰安": "370900", "威海": "371000", "日照": "371100",
           "临沂": "371300", "德州": "371400", "聊城": "371500", "滨州": "371600",
           "菏泽": "371700", "北京": "110000", "上海": "310000"}
LOCAL_RULE = re.compile(r"落户|户口|户籍|迁入|居住证|现行|通知|文号|政策|规定")
SOFTWARE = re.compile(r"系统里|平台里|页面|按钮|菜单|系统操作")
FOLLOWUP = re.compile(r"上一问|上面|刚才|那如果|这个条件|这个政策|换成|改成")
DATE = re.compile(r"((?:19|20)\d{2})\s*[-年]\s*(\d{1,2})\s*[-月]\s*(\d{1,2})\s*日?")
YEAR = re.compile(r"((?:19|20)\d{2})\s*年")
TARGET_YEAR = re.compile(r"(?:在|当时是|截至|按|到|回到|要查)\s*((?:19|20)\d{2})\s*年")
DOCUMENT_YEAR = re.compile(r"((?:19|20)\d{2})\s*年\s*的.{0,12}(?:通知|意见|规定)")


@dataclass(frozen=True)
class QueryPlan:
    question: str
    as_of: date
    region: str | None
    official_required: bool
    block_reason: str | None = None
    require_dated: bool = False
    period_start: date | None = None


def region_contains(code: str, region: str) -> bool:
    return (code in ("000000", region)
            or (code.endswith("0000") and code[:2] == region[:2])
            or (code.endswith("00") and code[:4] == region[:4]))


def region_codes(text: str) -> set[str]:
    return {code for name, code in REGIONS.items() if name in text}


def plan_query(payload: PolicyQuery, today: date) -> QueryPlan:
    question = payload.question
    users = [t.content for t in payload.history if t.role == "user"]
    if users and FOLLOWUP.search(question) and not any(topic in question for topic in TOPICS[:8]):
        topic = next((t for text in reversed(users) for t in TOPICS if t in text), None)
        if topic:
            question = f"办理{topic}时，{question}"
    sensitive = PRIVATE_DATA.search(question)
    unsafe = UNSAFE_ACTION.search(question) and not NEGATED_ACTION.search(question)
    if sensitive or unsafe:
        return QueryPlan(question, payload.asOf or today, payload.regionCode, False, "UNSAFE_OR_PRIVATE_REQUEST")
    if re.search(r"天气|股票|菜谱|量子|游戏", question) and not any(t in question for t in TOPICS):
        return QueryPlan(question, payload.asOf or today, payload.regionCode, False, "OUT_OF_SCOPE")
    # 只有年份而未提供完整 asOf 时，不猜测具体政策日期；要求明确证据适用性。
    target_years = set(TARGET_YEAR.findall(question))
    years = target_years or (set(YEAR.findall(question)) - set(DOCUMENT_YEAR.findall(question)))
    requested_date = payload.asOf
    period_start = None
    dates = DATE.findall(question)
    if dates:
        try:
            explicit_dates = {date(*(int(v) for v in match)) for match in dates}
        except ValueError:
            return QueryPlan(question, today, payload.regionCode, False, "INVALID_DATE")
        if len(explicit_dates) != 1 or (requested_date and requested_date not in explicit_dates):
            return QueryPlan(question, requested_date or today, payload.regionCode, False, "CONFLICTING_DATE")
        requested_date = explicit_dates.pop()
    elif len(years) > 1 or (requested_date and years and str(requested_date.year) not in years):
        return QueryPlan(question, requested_date or today, payload.regionCode, False, "CONFLICTING_DATE")
    elif years and requested_date is None:
        year = int(years.pop())
        period_start = date(year, 1, 1)
        requested_date = min(today, date(year, 12, 31)) if year == today.year else date(year, 12, 31)

    change = re.search(r"(?:换成|改成|换到|改到)([^，。？?]*)", payload.question)
    regions = region_codes(change[1] if change else payload.question)
    if not regions and not change and users and FOLLOWUP.search(question) and not REGIONAL_REQUEST.search(payload.question):
        regions = region_codes(users[-1])
    # 选择省内明确的市级范围。并列多个城市时要求明确目标。
    regions = {code for code in regions if not any(code != other and region_contains(code, other) for other in regions)}
    if len(regions) > 1 or (payload.regionCode and any(
            not (region_contains(code, payload.regionCode) or region_contains(payload.regionCode, code)) for code in regions)):
        return QueryPlan(question, requested_date or today, None, True, "CONFLICTING_REGION")
    region = next(iter(regions), payload.regionCode)
    if payload.regionCode and region and region_contains(region, payload.regionCode):
        region = payload.regionCode
    official = bool(OFFICIAL_REQUEST.search(question) or REGIONAL_REQUEST.search(question)
                    or (region and LOCAL_RULE.search(question) and not SOFTWARE.search(question))
                    or (change and (region or LOCAL_RULE.search(" ".join(users[-1:] + [question]))))
                    or (requested_date and requested_date > today))
    reason = "NO_APPLICABLE_SOURCE" if requested_date and requested_date > today else None
    return QueryPlan(question, requested_date or today, region, official, reason,
                     require_dated=requested_date is not None, period_start=period_start)


def eligible(chunk: Chunk, plan: QueryPlan) -> bool:
    if chunk.source_type == "POLICY_STATUS":
        return False
    if plan.require_dated and not chunk.effective_date:
        return False
    if chunk.effective_date and date.fromisoformat(chunk.effective_date) > (plan.period_start or plan.as_of):
        return False
    if chunk.expires_on and date.fromisoformat(chunk.expires_on) < plan.as_of:
        return False
    # 未标地区的系统操作指南可解释软件用法，但永远不能用于确认地方政策。
    generic_operation = not chunk.region_codes and chunk.source_type in ("PROJECT_GUIDE", "SYSTEM_GUIDE") and not plan.official_required
    if plan.region and not generic_operation:
        if not any(region_contains(code, plan.region) for code in chunk.region_codes):
            return False
    if plan.official_required:
        # 必须由知识库维护者明确录入来源和时间，不能由生成模型自行授予权威性。
        if chunk.source_type != "OFFICIAL_POLICY" or not chunk.source_url or not chunk.source_url.startswith("https://") or not chunk.effective_date:
            return False
        if not plan.region and chunk.region_codes != ("000000",):
            return False
    elif chunk.source_type == "OFFICIAL_POLICY":
        return False
    return True
