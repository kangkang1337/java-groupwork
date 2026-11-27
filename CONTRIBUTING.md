# 协作规范

本项目是 3~4 人小组的课程大作业，用**一个共享仓库 + 每人一条分支 + Pull Request** 的方式协作。
请所有组员先读一遍本文档再动手，能省掉 90% 的合并冲突。

## 一、分支怎么分

| 分支 | 用途 | 谁能推 |
| --- | --- | --- |
| `main` | 稳定版，随时能打包提交给老师 | 只能通过 PR 合入，**不直接 push** |
| `dev` | 集成分支，各人完成的功能先合到这里联调 | 组长合并 PR |
| `feat/<你的名字>` | **每人自己的分支**，日常所有提交都在这里 | 分支本人 |

命名示例：`feat/kangkang`、`feat/lisi`、`feat/wangwu`。

工作流：

```
组员在自己的 feat 分支开发 → push → 在 GitHub 上向 dev 提 PR → 组长 review 合并
dev 稳定后 → 组长向 main 提 PR → 合入 main（打 tag，用于最终交付）
```

**为什么不让大家直接推 main**：main 一旦被半成品污染，答辩前想回退就很难；
每人一条分支可以互不干扰地并行开发，合并时冲突范围也最小。

## 二、提交信息怎么写

统一用 `类型: 说明` 的格式，方便一眼看出改了什么：

```
feat: 新增图层混合模式
fix: 修复撤销后图层顺序错乱
docs: 补充开发文档中的模块说明
refactor: 把选区逻辑抽到 SelectionManager
style: 统一缩进与空行
chore: 更新 .gitignore
```

一次提交只做一件事。**不要把整块代码 + 格式调整 + 删除文件混在一个提交里**。

## 三、合并前自检

推 PR 之前，至少跑一遍：

```bat
build.bat
run-tests.bat
```

看到 `ALL TESTS PASSED` 再提交 PR。如果改动了绘图/选区/图层相关代码，
建议再手动点一遍界面确认没坏。

## 四、邀请组员加入仓库

1. 打开仓库页面 → 右上角 **Settings**
2. 左侧菜单拉到最下面 → **Collaborators**（新版界面在 `Access` 里）
3. 点 **Add people**，输入对方的 **GitHub 用户名**或注册邮箱
4. 选择权限：组员给 **Write**（能推分支、提 PR），组长保留 **Admin**
5. 对方会收到邮件邀请，**必须点接受**才算加入

> 私有仓库的协作者数量在免费账户下不限制，不用操心付费问题。

如果小组想让仓库归属更正式，可以：

1. 新建一个 GitHub **Organization**（免费）
2. 把仓库 `Transfer ownership` 转到这个组织下
3. 组织里建一个 **Team**，把组员都加进 Team，再给 Team 授权仓库
4. 好处：换组长、加人减人都不用改仓库设置；坏处：多一层管理成本

## 五、每人拿到自己的分支

组长在仓库里给每个人建好分支（或者让组员自己建也可以）：

```bash
# 方案 A：组长在网页端操作
#   仓库 → 分支下拉框 → View all branches → New branch
#   从 main 建 feat/组员名字

# 方案 B：命令行
git switch main
git pull
git switch -c feat/lisi
git push -u origin feat/lisi
```

组员拿到分支后，本地这样开始：

```bash
git clone https://github.com/kangkang1337/java-groupwork.git
cd java-groupwork
git switch feat/lisi        # 切到自己的分支
```

之后每天开工前先同步主分支，减少冲突：

```bash
git fetch origin
git merge origin/dev        # 或者 git rebase origin/dev
```

## 六、如果确实想要"各自的仓库"

如果组员之间不想互相看到中间过程，也可以各建一个仓库：

1. 每人 fork 一份主仓库（仓库页右上角 **Fork**）
2. 在自己 fork 里随便写，写完向主仓库提 **Pull Request**
3. 组长 review 后合并

**注意**：fork 模式下所有人仍然是"同一个上游仓库"，只是开发者各有一份副本。
对本课程来说，**推荐用本文第四节的 Collaborators + 分支方案**，更简单，也不会出现
"组员改动只在自己 fork 里、主仓库看不到"的情况。

## 七、组员分工表（组长填写）

| 姓名 | GitHub 用户名 | 分支 | 负责模块 | 权重 |
| --- | --- | --- | --- | --- |
|  |  | `feat/` |  | 1.0 |
|  |  | `feat/` |  | 1.0 |
|  |  | `feat/` |  | 1.0 |
|  |  | `feat/` |  | 1.0 |

> 权重范围 0.9~1.1，缺省 1.0，最终填进开发文档。

## 八、不要提交这些东西

`.gitignore` 已经挡住了，这里再强调一遍：

- `out/`、`*.class` —— 编译产物，每人本地重新编译即可
- `logs/` —— 运行日志
- `.idea/`、`.vscode/` —— 个人 IDE 配置

`lib/` 里的 JavaFX 运行时**要提交**，这样组员拉下来就能直接 `run.bat`，不用配环境。
