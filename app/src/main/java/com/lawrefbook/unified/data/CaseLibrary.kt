package com.lawrefbook.unified.data

/**
 * 案例库（内置示例数据占位）：裁判规则、类案判决、人民法院案例库案例。
 * 后续可替换为在线数据源或完整离线案例库。
 */
data class CaseEntity(
    val id: String,
    val title: String,          // 案例名称
    val caseNo: String,         // 案号 / 入库编号
    val caseType: String,       // 民事 / 刑事 / 行政 / 执行
    val court: String,          // 审理/入库机构
    val date: String,           // 裁判/入库日期
    val category: CaseCategory, // 归类：裁判规则 / 类案判决 / 案例库
    val gist: String,           // 裁判要旨（关键词组）
    val summary: String,        // 摘要
    val detail: String          // 案例详情（正文）
)

enum class CaseCategory(val label: String) {
    RULES("裁判规则"),
    SIMILAR("类案判决"),
    LIBRARY("人民法院案例库")
}

object CaseLibrary {

    val cases: List<CaseEntity> = listOf(
        // ── 裁判规则（指导性案例裁判要旨） ──
        CaseEntity(
            id = "r1",
            title = "中信银行股份有限公司某分行金融借款合同纠纷案",
            caseNo = "指导案例 168 号",
            caseType = "民事",
            court = "最高人民法院",
            date = "2021-11-09",
            category = CaseCategory.RULES,
            gist = "合同解除 · 恢复原状 · 违约责任",
            summary = "合同解除后，尚未履行的终止履行；已履行的，根据履行情况和合同性质，当事人可以请求恢复原状或采取其他补救措施，并有权请求赔偿损失。",
            detail = "【基本案情】某银行与某公司签订金融借款合同，后因该公司未按约还款，银行请求解除合同。\n\n【裁判要点】合同解除后，尚未履行的终止履行；已履行的，根据履行情况和合同性质，当事人可以请求恢复原状或采取其他补救措施，并有权请求赔偿损失。解除权人有权请求违约方承担违约责任。"
        ),
        CaseEntity(
            id = "r2",
            title = "赵某与某网络公司个人信息保护纠纷案",
            caseNo = "指导案例 190 号",
            caseType = "侵权责任",
            court = "最高人民法院",
            date = "2022-06-28",
            category = CaseCategory.RULES,
            gist = "个人信息 · 同意 · 最小必要",
            summary = "处理个人信息应当遵循合法、正当、必要原则，未经同意收集、使用个人信息的，依法承担侵权责任。",
            detail = "【基本案情】某网络公司未经用户同意收集并对外提供用户个人信息，用户诉请停止侵害、赔偿损失。\n\n【裁判要点】处理个人信息应当遵循合法、正当、必要原则，不得过度处理。未经自然人同意收集、使用其个人信息，侵害个人信息权益的，应当承担停止侵害、赔偿损失等侵权责任。"
        ),
        CaseEntity(
            id = "r3",
            title = "孙某危险驾驶案",
            caseNo = "指导案例 32 号",
            caseType = "刑事",
            court = "最高人民法院",
            date = "2015-12-25",
            category = CaseCategory.RULES,
            gist = "醉酒驾驶 · 危险驾驶罪 · 入罪标准",
            summary = "行为人在道路上醉酒驾驶机动车的，属于追逐竞驶型、醉酒型危险驾驶，血液酒精含量达到入罪标准的，以危险驾驶罪定罪处罚。",
            detail = "【基本案情】孙某醉酒后驾驶机动车在道路上行驶，发生交通事故。\n\n【裁判要点】醉酒驾车，血液酒精含量达到入罪标准的，应当以危险驾驶罪追究刑事责任，并依法从重处罚。"
        ),

        // ── 类案判决 ──
        CaseEntity(
            id = "s1",
            title = "李某诉某物业公司物业服务合同纠纷案",
            caseNo = "(2023)京03民终1234号",
            caseType = "民事",
            court = "北京市第三中级人民法院",
            date = "2023-05-20",
            category = CaseCategory.SIMILAR,
            gist = "物业服务 · 瑕疵履行 · 物业费减免",
            summary = "物业服务存在明显瑕疵的，业主有权请求酌减相应物业费；但一般性瑕疵不足以构成拒交物业费的理由。",
            detail = "【基本案情】业主李某以物业服务不达标为由拒交物业费，物业公司诉请支付。\n\n【裁判要旨】物业服务企业未完全履行合同义务，存在明显瑕疵的，可以酌减物业费；但瑕疵尚未达到免除合同义务程度的，业主仍应支付相应物业费。"
        ),
        CaseEntity(
            id = "s2",
            title = "王某诉某保险公司机动车交通事故责任纠纷案",
            caseNo = "(2023)沪74民终567号",
            caseType = "民事",
            court = "上海市金融法院",
            date = "2023-09-12",
            category = CaseCategory.SIMILAR,
            gist = "保险合同 · 免责条款 · 说明义务",
            summary = "保险人未就免责条款履行提示和明确说明义务的，该条款不产生效力，保险人仍应承担赔付责任。",
            detail = "【基本案情】投保人发生保险事故后，保险公司以免责条款拒赔。\n\n【裁判要旨】对免除保险人责任的条款，保险人未在投保时作出足以引起投保人注意的提示，也未对该条款内容作出明确说明的，该条款不产生效力。"
        ),
        CaseEntity(
            id = "s3",
            title = "周某与某区市场监管局行政处罚案",
            caseNo = "(2023)鄂01行终89号",
            caseType = "行政",
            court = "湖北省武汉市中级人民法院",
            date = "2023-11-08",
            category = CaseCategory.SIMILAR,
            gist = "行政处罚 · 过罚相当 · 裁量基准",
            summary = "行政处罚应当遵循过罚相当原则，综合考虑违法行为的事实、性质、情节和社会危害程度，避免畸轻畸重。",
            detail = "【基本案情】周某因轻微违法被处以明显超过违法情节的罚款，诉请撤销。\n\n【裁判要旨】行政处罚决定应当遵循过罚相当原则，处罚幅度应与违法行为的社会危害程度相当。明显畸重的，人民法院依法予以变更。"
        ),

        // ── 人民法院案例库 ──
        CaseEntity(
            id = "l1",
            title = "某银行与某公司金融借款合同纠纷案",
            caseNo = "入库编号 2024-08-2-103-001",
            caseType = "民事",
            court = "人民法院案例库",
            date = "2024-08-15",
            category = CaseCategory.LIBRARY,
            gist = "合同解除 · 恢复原状 · 违约责任",
            summary = "合同解除后尚未履行的，终止履行；已履行的，根据履行情况和合同性质恢复原状或采取其他补救措施。",
            detail = "【基本案情】某银行与某公司签订金融借款合同，公司未按约还款，银行请求解除合同。\n\n【裁判要旨】合同解除后，尚未履行的终止履行；已履行的，根据履行情况和合同性质，当事人可以请求恢复原状或采取其他补救措施，并有权请求赔偿损失。"
        ),
        CaseEntity(
            id = "l2",
            title = "张某诉某网络公司个人信息保护纠纷案",
            caseNo = "入库编号 2024-07-2-372-012",
            caseType = "侵权责任",
            court = "人民法院案例库",
            date = "2024-07-30",
            category = CaseCategory.LIBRARY,
            gist = "个人信息 · 同意 · 最小必要",
            summary = "处理个人信息应当遵循合法、正当、必要原则，未经同意收集、使用个人信息的，依法承担侵权责任。",
            detail = "【基本案情】网络公司未经同意收集并对外提供用户个人信息，用户诉请赔偿。\n\n【裁判要旨】处理个人信息应当遵循合法、正当、必要原则。未经同意收集、使用个人信息，侵害个人信息权益的，应承担侵权责任。"
        ),
        CaseEntity(
            id = "l3",
            title = "李某危险驾驶案",
            caseNo = "入库编号 2024-06-2-133-008",
            caseType = "刑事",
            court = "人民法院案例库",
            date = "2024-06-20",
            category = CaseCategory.LIBRARY,
            gist = "醉酒驾驶 · 危险驾驶罪",
            summary = "醉酒驾驶机动车，血液酒精含量达到入罪标准的，以危险驾驶罪定罪处罚。",
            detail = "【基本案情】李某醉酒后驾驶机动车在道路上行驶。\n\n【裁判要旨】醉酒驾驶机动车，血液酒精含量达到入罪标准的，以危险驾驶罪定罪处罚。"
        ),
        CaseEntity(
            id = "l4",
            title = "王某与某行政机关行政处罚案",
            caseNo = "入库编号 2024-05-2-012-005",
            caseType = "行政",
            court = "人民法院案例库",
            date = "2024-05-10",
            category = CaseCategory.LIBRARY,
            gist = "行政处罚 · 过罚相当",
            summary = "行政处罚应当遵循过罚相当原则，综合考虑违法行为的事实、性质、情节及社会危害程度。",
            detail = "【基本案情】王某因违法被处以与其情节不相当的罚款，诉请变更。\n\n【裁判要旨】行政处罚应当遵循过罚相当原则，处罚决定应综合考虑违法行为的事实、性质、情节及社会危害程度。"
        )
    )

    fun byCategory(category: CaseCategory): List<CaseEntity> = cases.filter { it.category == category }

    fun byId(id: String): CaseEntity? = cases.firstOrNull { it.id == id }
}
