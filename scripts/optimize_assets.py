#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
优化 LawRefBook 法条资源，减小 APK 体积：
1. Laws/db.sqlite3 内含 FTS5 全文索引表（fts/fts_data/fts_idx/fts_content/fts_docsize/fts_config），
   而本 App 使用 LIKE 子串搜索、从不查询 FTS，这些表纯属冗余，且占去约 120MB。
   -> 删除 fts* 表并 VACUUM。
2. 将整个 Laws/ 目录打包为单个 assets/laws.zip（高压缩率），运行时首次启动解压到
   应用私有目录；避免 5000+ 散文件直接进 APK（散文件压缩率低、APK 膨胀）。
3. 删除原始散目录 Laws/。
"""
import os
import sqlite3
import zipfile
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LAWS_DIR = os.path.join(ROOT, "app", "src", "main", "assets", "Laws")
DB = os.path.join(LAWS_DIR, "db.sqlite3")
ZIP_OUT = os.path.join(ROOT, "app", "src", "main", "assets", "laws.zip")


def human(n):
    return f"{n/1024/1024:.2f} MB" if n >= 1024 * 1024 else f"{n/1024:.1f} KB"


def main():
    if not os.path.isfile(DB):
        print(f"[ERROR] 未找到 {DB}，请先运行 prepare_assets.py")
        sys.exit(1)

    print("== 1) 分析 db.sqlite3 ==")
    con = sqlite3.connect(DB)
    cur = con.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
    tables = [r[0] for r in cur.fetchall()]
    print("   tables:", tables)
    for t in tables:
        cur.execute(f"SELECT count(*) FROM {t}")
        n = cur.fetchone()[0]
        print(f"   [{t}] rows={n}")
    con.close()

    print("\n== 2) 删除冗余表（app 使用 LIKE 搜索 .md 正文，不查 fts/item）==")
    con = sqlite3.connect(DB)
    cur = con.cursor()
    cur.execute("SELECT name FROM sqlite_master WHERE type='table' AND (name LIKE 'fts%' OR name='item')")
    fts = [r[0] for r in cur.fetchall()]
    for t in fts:
        cur.execute(f"DROP TABLE IF EXISTS {t}")
    con.commit()
    cur.execute("VACUUM")
    con.close()
    print(f"   已删除: {fts}")
    print(f"   db 体积(删 fts 后): {human(os.path.getsize(DB))}")

    print("\n== 3) 打包 Laws/ -> laws.zip ==")
    if os.path.exists(ZIP_OUT):
        os.remove(ZIP_OUT)
    total = 0
    # 只打包 App 实际消费的内容，排除冗余：
    #   - DLC/        地方性法规扩展（App 主目录库 db.sqlite3 未收录，运行时从不读取）
    #   - scrape/ scripts/ .github/  上游仓库的爬虫/建库工具与 CI 配置
    #   - 点开头文件（.gitignore/.lawignore/.gitattributes 等元数据）
    SKIP_DIRS = {"DLC", "scrape", "scripts", ".github"}
    with zipfile.ZipFile(ZIP_OUT, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for root, dirs, files in os.walk(LAWS_DIR):
            dirs[:] = [d for d in dirs if d not in SKIP_DIRS and not d.startswith(".")]
            for f in sorted(files):
                if f.startswith("."):
                    continue
                fp = os.path.join(root, f)
                arc = os.path.relpath(fp, LAWS_DIR)
                z.write(fp, arc)
                total += os.path.getsize(fp)
    print(f"   源文件总大小: {human(total)}")
    print(f"   laws.zip 大小: {human(os.path.getsize(ZIP_OUT))}")

    print("\n== 4) 删除散目录 Laws/ ==")
    shutil.rmtree(LAWS_DIR)
    print(f"   已删除 {LAWS_DIR}")

    print("\n[DONE] laws.zip 已就绪，APK 将改为运行时解压该 zip。")


if __name__ == "__main__":
    main()
