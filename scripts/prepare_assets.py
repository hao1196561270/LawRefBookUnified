#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
prepare_assets.py — 把开源法条数据源（LawRefBook/Laws）灌入 Android 工程的 assets。

数据源：https://github.com/LawRefBook/Laws （公有领域法条，与 xloger/LawRefBookAndroid、
IncoderApp/LawRefBook 同源）。仓库结构：
    Laws/
        db.sqlite3              # 目录库：category / law 两张表
        <分类文件夹>/<法规>.md   # Markdown 法条正文

本工程在运行时从 assets/Laws/ 读取 db.sqlite3 与各 .md 文件，
路径解析与已验证的 resolvePath 一致。

用法：
    python scripts/prepare_assets.py --source <Laws仓库路径> [--dest app/src/main/assets/Laws]

例如（在本仓库已克隆的研究副本上）：
    python scripts/prepare_assets.py \
        --source ../research/lawrefbook-Laws \
        --dest app/src/main/assets/Laws
"""
import argparse
import os
import shutil
import sys


def copy_tree(src: str, dst: str, skip_names=None):
    """递归复制目录，跳过 .git 等。返回复制文件数。"""
    skip_names = skip_names or {".git"}
    count = 0
    for root, dirs, files in os.walk(src):
        dirs[:] = [d for d in dirs if d not in skip_names]
        rel = os.path.relpath(root, src)
        target_dir = os.path.join(dst, rel) if rel != "." else dst
        os.makedirs(target_dir, exist_ok=True)
        for f in files:
            shutil.copy2(os.path.join(root, f), os.path.join(target_dir, f))
            count += 1
    return count


def main():
    ap = argparse.ArgumentParser(description="将 Laws 数据源灌入 assets")
    ap.add_argument("--source", required=True, help="LawRefBook/Laws 仓库根目录")
    ap.add_argument("--dest", default="app/src/main/assets/Laws",
                    help="目标 assets 目录（默认 app/src/main/assets/Laws）")
    args = ap.parse_args()

    source = os.path.abspath(args.source)
    dest = os.path.abspath(args.dest)

    if not os.path.isdir(source):
        sys.exit(f"[错误] 源目录不存在：{source}")
    db_src = os.path.join(source, "db.sqlite3")
    if not os.path.isfile(db_src):
        sys.exit(f"[错误] 未在源目录找到 db.sqlite3：{db_src}")

    os.makedirs(dest, exist_ok=True)

    # 1) 复制目录库
    shutil.copy2(db_src, os.path.join(dest, "db.sqlite3"))
    print(f"[ok] 已复制 db.sqlite3 -> {os.path.join(dest, 'db.sqlite3')}")

    # 2) 复制所有分类文件夹（含 *.md）
    n = copy_tree(source, dest, skip_names={".git"})
    print(f"[ok] 共复制 {n} 个文件到 {dest}")

    # 3) 自检：db.sqlite3 与各文件夹是否就位
    print("[info] 校验 assets/Laws 结构：")
    for entry in sorted(os.listdir(dest)):
        full = os.path.join(dest, entry)
        if os.path.isdir(full):
            md = [f for f in os.listdir(full) if f.endswith(".md")]
            print(f"      {entry}/  ({len(md)} 个 .md)")
        else:
            print(f"      {entry}")
    print("[done] 资源准备完成。请用 Android Studio 打开并构建。")


if __name__ == "__main__":
    main()
