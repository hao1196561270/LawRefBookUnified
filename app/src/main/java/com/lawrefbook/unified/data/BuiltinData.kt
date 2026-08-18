package com.lawrefbook.unified.data

/**
 * 内置法条数据的版本锚点。
 * 由 optimize_assets.py 处理时对应的上游提交；用于月度自动更新做版本比对。
 */
object BuiltinData {
    /** 内置数据对应的上游提交 SHA（与 research/lawrefbook-Laws 的 HEAD 一致） */
    const val COMMIT = "24f29392293ce672d608e19c2bffff10401fe6a8-local-02"

    /** 内置数据发布日期（上游提交日期） */
    const val DATE = "2026-08-17"

    /** 上游仓库：最新提交查询（GitHub API） */
    const val REPO_API = "https://api.github.com/repos/LawRefBook/Laws/commits?per_page=1"

    /** 上游仓库：master 分支快照 zip（用于同步最新内容） */
    const val REPO_ZIP = "https://github.com/LawRefBook/Laws/archive/refs/heads/master.zip"
}

