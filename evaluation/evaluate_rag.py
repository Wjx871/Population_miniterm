"""同语料/同问题的检索消融与少量真实生成验收；结果不是政策准确率。"""
import argparse
from datetime import date, datetime, timezone
from hashlib import sha256
import json
from pathlib import Path
import statistics
import sys
import time

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'ai-service'))
from yixun_ai.app import DEFAULT_KNOWLEDGE
from yixun_ai.generation import GeneratorConfig,GroundedGenerator
from yixun_ai.knowledge import KnowledgeIndex
from yixun_ai.models import PolicyQuery
from yixun_ai.rag import RagPipeline
from yixun_ai.semantic import SemanticModels


def grade(case,result):
    sources=[c.logicalPath for c in result.citations]
    relevant=case['acceptable_sources']
    ranks=[i+1 for i,p in enumerate(sources) if p in relevant]
    expected=case['expected_behavior']
    shape=bool(ranks) if expected=='cite_guide' else result.mode=='SAFE_REFUSAL' and not sources if expected=='refuse' else result.evidenceStatus=='INSUFFICIENT' and not sources
    return {'document_hit_at_4':bool(ranks) if relevant else None,
            'reciprocal_rank_at_4':1/min(ranks) if ranks else 0 if relevant else None,
            'behavior_shape':shape,'human_answer_grade':None}


def summary(rows):
    positive=[r for r in rows if r['checks']['document_hit_at_4'] is not None]
    timing=[r['response']['timingMs'] for r in rows]
    return {'cases':len(rows),'positive_cases':len(positive),
            'document_hits_at_4':sum(r['checks']['document_hit_at_4'] for r in positive),
            'mrr_at_4':statistics.mean(r['checks']['reciprocal_rank_at_4'] for r in positive) if positive else None,
            'behavior_shape_passes':sum(r['checks']['behavior_shape'] for r in rows),
            'latency_ms_p50':statistics.median(timing),
            'verified_generation_cases':sum(r['response']['generationMode']=='VERIFIED_EXTRACTIVE' for r in rows),
            'generation_fallback_cases':sum(r['response']['generationMode']=='EXTRACTIVE_FALLBACK' for r in rows),
            'failed_cases':[r['id'] for r in rows if not r['checks']['behavior_shape']]}


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--dataset',type=Path,default=ROOT/'evaluation/datasets/rag_challenge.v2.jsonl')
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--generation-url',default='')
    p.add_argument('--generation-model',default='yixun-local')
    p.add_argument('--generation-count',type=int,default=0)
    args=p.parse_args()
    if args.output.exists():p.error('Do not overwrite a previous result file')
    raw=args.dataset.read_bytes()
    if args.dataset.with_suffix('.sha256').exists():
        assert sha256(raw).hexdigest()==args.dataset.with_suffix('.sha256').read_text().strip(),'Challenge set modified'
    cases=[json.loads(line) for line in raw.decode().splitlines() if line.strip()]
    idx=KnowledgeIndex(DEFAULT_KNOWLEDGE)
    print('Loading pinned semantic models...',flush=True)
    t=time.perf_counter()
    semantic=SemanticModels(ROOT/'ai-service/cache/models',ROOT/'ai-service/cache/index',idx)
    load_ms=(time.perf_counter()-t)*1000
    report={'created_at_utc':datetime.now(timezone.utc).isoformat(),'dataset':args.dataset.name,
            'dataset_sha256':sha256(raw).hexdigest(),'knowledge_version':idx.version,
            'models':semantic.versions,'model_manifest_sha256':sha256((ROOT/'ai-service/model-manifest.json').read_bytes()).hexdigest(),
            'model_load_ms':load_ms,'conditions':{'concurrency':1,'repetitions':1,'generation_count':args.generation_count,'as_of':'2026-11-01'},
            'limitations':['人工构造诊断集，不是经过独立用户评审的泛化测试。','自动检查引用文档命中和拒答形态，不等于答案正确率。','离线流水线耗时，与此前Java HTTP耗时口径不同。'],
            'experiments':{}}
    for name,pipeline in [('lexical_scoped',RagPipeline(idx)),('hybrid_rrf',RagPipeline(idx,semantic,rerank=False)),('hybrid_rerank',RagPipeline(idx,semantic))]:
        rows=[]
        for c in cases:
            payload={k:c[k] for k in ('question','history','regionCode','asOf') if k in c}
            result=pipeline.query(PolicyQuery(**payload),c['id'],date(2026,11,1))
            rows.append({'id':c['id'],'question':c['question'],'checks':grade(c,result),'response':result.model_dump()})
        report['experiments'][name]={'summary':summary(rows),'results':rows}
        print(name,report['experiments'][name]['summary'],flush=True)
    if args.generation_count:
        if not args.generation_url:p.error('generation-url required for generation validation')
        generator=GroundedGenerator(GeneratorConfig(args.generation_url,args.generation_model,timeout=60))
        pipeline=RagPipeline(idx,semantic,generator)
        rows=[]
        for c in [c for c in cases if c['expected_behavior']=='cite_guide'][:args.generation_count]:
            payload={k:c[k] for k in ('question','history','regionCode','asOf') if k in c}
            result=pipeline.query(PolicyQuery(**payload),c['id'],date(2026,11,1))
            rows.append({'id':c['id'],'question':c['question'],'checks':grade(c,result),'response':result.model_dump()})
            print(c['id'],result.mode,result.generationMode,round(result.timingMs),'ms',flush=True)
        report['experiments']['grounded_generation']={'summary':summary(rows),'results':rows}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    with args.output.open('x') as f:json.dump(report,f,ensure_ascii=False,indent=2);f.write('\n')
    print('Saved',args.output,flush=True)

if __name__=='__main__':main()
