真实赛道走线（已内置）
======================

data/tracks/*.svg 是从 Wikimedia Commons 下载的赛道矢量图，
SvgTracks 从里面提取一条主路径作为赛道走线，车沿它行驶。

来源与署名
----------
| 文件 | 赛道 | 来源文件（Wikimedia Commons） | 作者/许可 |
| --- | --- | --- | --- |
| imola.svg | 伊莫拉 | Imola.svg | Will Pittenger（CC BY-SA） |
| spa.svg | 斯帕-弗朗科尔尚 | Spa-Francorchamps of Belgium.svg | Will Pittenger（CC BY-SA） |
| lemans.svg | 勒芒 萨尔特 | Circuit de la Sarthe track map.svg | Will Pittenger（CC BY-SA） |
| saopaulo.svg | 因特拉戈斯 | Autódromo José Carlos Pace (AKA Interlagos) track map.svg | Will Pittenger（CC BY-SA） |
| cota.svg | COTA | F1 circuits 2014-2018 - Circuit of the Americas (version 2).svg | 文件元数据未标注作者，见来源页 |
| fuji.svg | 富士 | Circuit Fuji.svg | 文件元数据未标注作者，见来源页 |
| barcelona.svg | 巴塞罗那 | Circuit Catalunya 2007.svg | 文件元数据未标注作者，见来源页 |
| monza.svg | 蒙扎 | Monza-2021.svg | 文件元数据未标注作者，见来源页 |

> 作者信息取自 SVG 内嵌的 dc:creator 元数据；没标注的四张，
> 请到对应文件页面核对后再补。本项目仅用于课程作业演示。

重要说明：这是"主路径提取"，不是官方中心线
--------------------------------------------
SvgTracks.parse() 用启发式挑主路径：

    score = 路径点数 × √外框面积

这个启发式假设"赛道外圈是图里点数最多、占地最大的那条 path"。
绝大多数情况成立，但它**不保证**取到的就是赛会官方定义的赛道中心线——
装饰线、跑道边缘、多布局拼图都可能干扰。

因此本项目对赛道形状的表述是"由官方 SVG 主路径提取的示意走线"，
而不是"精确复刻官方中心线"。若需要更高保真，可以为特殊赛道
单独指定 path 索引，而不是全部依赖同一个启发式。

已知取舍
--------
- 巴塞罗那用的是 2007 版布局（比现在多一个末段减速弯）：
  2021/2023 版的 SVG 里赛道与装饰元素混在一起，启发式会挑错。
- 其余七条均已人工核对形状（蒙扎的 Lesmo / Ascari / Parabolica、COTA 的 1 号弯等可辨认）。

替换/新增
---------
把任意 SVG（含 <path d="...">）改名成 <赛道id>.svg 放进本目录即可；
没有对应文件就退回内置手绘形状，不会报错。

调试：java -cp out/classes com.endurance.dev.TrackCheck
