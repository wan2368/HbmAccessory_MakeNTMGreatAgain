# 资源结构

- `lang/`：`en_US.lang` / `zh_CN.lang`
- `textures/items/`：物品贴图 16×16
- `textures/blocks/`：方块贴图
- `textures/gui/`：GUI 背景
- `models/`：1.7.10 模型
- `pinyin.tsv`：拼音搜索字典

新增资源后必须重新构建；语言修改需要重启客户端。

## pinyin.tsv 维护

拼音搜索靠 `PinyinSearchTransformer` 把 HBM GUI 里的 `String.contains` 换成 `PinyinSearch.contains`，
字典覆盖 GB2312 全集（6765 字）。**新增物品 / 流体 / 配方 / 成就之后**必须跑一次：

```
python tools/pinyin_audit.py          # 有缺字时退出码 1
python tools/pinyin_audit.py --fix    # 自动补字（需 pip install pypinyin）
```

注意：NEI 的搜索框走正则匹配，不在这套拼音搜索的覆盖范围内。
