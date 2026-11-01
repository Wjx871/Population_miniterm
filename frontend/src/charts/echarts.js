/**
 * 【数据大屏链路 · ECharts 按需注册】
 * 只注册大屏用到的折线/柱状/饼图 + 网格/提示/图例等组件，减小打包体积。
 * 导出 initEChart 供 BaseEChart.vue 挂载到 DOM；真正 setOption 在 BaseEChart 中完成。
 */
import { init, use } from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent, DatasetComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'

use([LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, DatasetComponent, CanvasRenderer])

export { init as initEChart }
