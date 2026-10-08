# -*- coding: utf-8 -*-
"""把 Js.java 里的 text block 抽成可独立校验的 np_inject.js。

Java text block 会剥离公共缩进，这里做同样的处理，保证抽出的脚本
与运行时 evaluateJavascript 送进 WebView 的内容一致。
用法：python tools/extract_js.py   （在 northplus 目录下执行）
"""
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "app", "src", "main", "java",
                   "net", "northplus", "app", "Js.java")
OUT = os.path.join(HERE, "np_inject.js")


def main():
    src = open(SRC, encoding="utf-8").read()
    m = re.search(r'private static final String TEMPLATE = """(.*?)\n            """;',
                  src, re.S)
    if not m:
        raise SystemExit("未找到 TEMPLATE text block")
    lines = m.group(1).split("\n")
    indent = min((len(l) - len(l.lstrip())) for l in lines if l.strip())
    body = "\n".join(l[indent:] if l.strip() else "" for l in lines)
    body = body.replace("__CFG__",
                        '{"night":true,"noImage":true,"adBlock":true,"tapImage":true}')
    with open(OUT, "w", encoding="utf-8") as f:
        f.write(body)
    bs = body.count("\\")
    print("已写出 %s (%d 字节, 反斜杠 %d 处)" % (OUT, len(body), bs))
    if bs:
        print("注意：脚本里出现反斜杠，需确认 Java text block 的转义是否按预期")


if __name__ == "__main__":
    main()
