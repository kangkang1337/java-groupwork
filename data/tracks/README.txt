真实赛道走线（已内置）
======================

data/tracks/*.svg 是从 Wikimedia Commons 下载的官方赛道矢量图，
游戏会自动解析出赛道中心线，车沿真实走线行驶。

| 文件 | 赛道 | 来源文件（Wikimedia Commons） |
| --- | --- | --- |
| imola.svg | 伊莫拉 | Imola.svg |
| spa.svg | 斯帕-弗朗科尔尚 | Spa-Francorchamps of Belgium.svg |
| lemans.svg | 勒芒 萨尔特 | Circuit de la Sarthe track map.svg |
| saopaulo.svg | 因特拉戈斯 | Autódromo José Carlos Pace (AKA Interlagos) track map.svg |
| cota.svg | COTA | F1 circuits 2014-2018 - Circuit of the Americas (version 2).svg |
| fuji.svg | 富士 | Circuit Fuji.svg |
| barcelona.svg | 巴塞罗那 | Circuit Catalunya 2007.svg |
| monza.svg | 蒙扎 | Monza-2021.svg |

> 注意：巴塞罗那用的是 2007 版布局（比现在的多一个末段减速弯），
> 因为 2021/2023 版的 SVG 里赛道外圈和装饰元素混在一起，自动识别会挑错。

授权
----
这些图来自 Wikimedia Commons，多为 CC BY-SA 或公有领域。
本项目仅用于课程作业演示，保留原图作者署名权。

替换/新增
---------
把任意 SVG（含 <path d="...">）改名成 <赛道id>.svg 放进本目录即可，
程序会自动挑"点多且占画面大"的那条 path 当作赛道外圈。
没有文件就退回内置手绘形状。

调试：java -cp out/classes com.endurance.dev.TrackCheck
