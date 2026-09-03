window.businessArchitectureData = {
  meta: {
    title: "AI Learn WMS · 业务架构",
    version: "一期目标设计 · 2026-08-28",
    statement: "由单据表达业务意图，由显式状态控制执行，由库存、制造与设备事实支撑追溯。",
    disclaimer: "架构页面基于 docs/specs 已确认的一期业务规则；页面属于目标设计，不表示后端业务接口已经实现。",
    reviewUpdate: {
      completed: "库存、销售、采购、生产质检、IoT、GIS 看板和 AI 助手的一期业务规则均已完成逐项确认；销售直接拣货与采购先隔离后质检的最新修正已同步。",
      next: "业务规则复核已经完成；下一阶段按黄金闭环顺序制作并验证采购、生产与其他模块高保真原型。"
    }
  },
  modules: [
    {
      id: "sales",
      code: "01 / DEMAND",
      title: "销售",
      page: "销售业务架构.html",
      confidence: "confirmed",
      confidenceLabel: "已确认",
      tagline: "客户需求与交付进度的业务主线",
      purpose: "记录客户需求，控制订单生命周期，并通过库存履约事实表达分批交付进度。",
      inputs: ["客户与商品主数据", "计划发货日期", "人工供需来源", "仓库履约结果"],
      outputs: ["销售订单", "履约数量进度", "正常/人工完成审计", "跨域追溯入口"],
      roles: ["销售人员", "仓库人员"],
      states: ["生命周期：Draft（未提交）→ Submitted（已提交）→ Approved（已审核）→ Completed（已完成）", "履约进度：NotStarted（未开始）→ InProgress（进行中）→ FullyShipped（全部发货）", "完成方式：未完成 / Normal（正常完成）/ Manual（人工完成）"],
      stateAxes: [
        { label: "生命周期", values: ["未提交", "已提交", "已审核", "已完成"] },
        { label: "履约进度", values: ["未开始", "进行中", "全部发货"] },
        { label: "完成方式", values: ["未完成", "正常完成", "人工完成"] }
      ],
      flow: [
        { id: "sales-create", title: "创建订单", owner: "销售人员", detail: "记录客户、产品、数量和计划发货日期，进入 Draft（未提交）；只有未提交可以修改核心字段。" },
        { id: "sales-submit", title: "提交与审核", owner: "销售人员", detail: "未提交订单进入 Submitted（已提交）；审核后进入 Approved（已审核），但不自动预留库存。" },
        { id: "sales-pick", title: "分批直接拣货", owner: "仓库人员", detail: "同一事务内自动补足本次数量所需预留，再将实物和等量有效预留分配从来源库位同步迁移到 ShippingStaging（发货暂存位）；任一步失败时整体回滚。" },
        { id: "sales-ship", title: "分批发货", owner: "仓库人员", detail: "从发货暂存位扣减实物，释放对应有效预留并累计 shipped_qty。" },
        { id: "sales-complete", title: "完成订单", owner: "销售 / 仓库", detail: "全部发货自动正常完成；终止剩余履约由销售填写原因后显示已完成（人工），实际发货数量保持事实值。" }
      ],
      relations: [
        { direction: "upstream", module: "主数据", text: "引用客户、商品、计量单位，不复制主数据事实。" },
        { direction: "downstream", module: "库存", text: "直接拣货调用库存应用服务原子完成自动预留与移位，发货再扣减实物；销售模块禁止直接修改余额。" },
        { direction: "support", module: "生产 / 采购", text: "一期只保存人工来源关联，不自动生成工单或采购建议。" },
        { direction: "read", module: "追溯 / 看板 / AI", text: "只读投影销售与履约事实，不建立第二套订单状态。" }
      ],
      ownedFacts: ["销售订单生命周期", "订单行 ordered/reserved/picked/shipped 累计量", "完成方式与完成审计", "客户需求与计划交期"],
      boundaries: [
        "销售模块不直接修改 InventoryBalance、预留分配或库存流水。",
        "审核不等于预留，也不自动触发生产或采购。",
        "Completed 是终态；人工完成不得补造拣货、发货或库存流水。",
        "财务、收款、发票、退货与发货冲销不进入一期已确认链路。"
      ],
      exceptions: [
        "非法状态或无权限命令必须拒绝。",
        "并发直接拣货中的自动预留不得导致 available_qty（可用数量）< 0。",
        "数量始终满足 0 ≤ shipped ≤ picked ≤ reserved ≤ ordered。",
        "同一幂等键不同载荷必须冲突，重复命令不得重复记账。",
        "人工完成前若存在未发货暂存数量，必须先退回拣货。",
        "退回拣货与释放未拣预留只用于异常恢复或终止，不属于正常履约顺序。",
        "订单、库存、库位与来源关联不得跨租户。"
      ],
      extensions: ["销售退货与发货冲销", "订单变更版本", "ATP 与缺口计算", "多仓拆单履约", "承运商与签收", "价格、信用、应收与开票"],
      specs: [
        { label: "销售订单业务规则", href: "../specs/10-erp-wms/销售订单业务规则.md" },
        { label: "ERP/WMS 领域模型", href: "../specs/10-erp-wms/领域模型.md" },
        { label: "ERP/WMS 接口契约", href: "../specs/10-erp-wms/接口契约.md" }
      ]
    },
    {
      id: "purchasing",
      code: "02 / SUPPLY",
      title: "采购",
      page: "采购业务架构.html",
      confidence: "confirmed",
      confidenceLabel: "已确认",
      tagline: "外部供应与原料入库的业务主线",
      purpose: "记录供应商交付意图，通过到货外观验收、收货前拒收、实际接收、到货质检、质量处置与上架把外部供应转化为可信库存事实。",
      inputs: ["供应商与商品主数据", "人工关联的生产来源", "预计到货日期", "仓库到货验收结果", "生产质检结果"],
      outputs: ["采购订单", "到货拒收与分批收货记录", "质量处置决定及执行记录", "上架任务与入库追溯"],
      roles: ["采购人员", "仓库人员", "生产质检人员"],
      states: ["Draft（未提交）→ Submitted（已提交）→ Approved（已审核）→ PartiallyReceived（部分收货）→ Completed（已完成）", "完成方式：Normal（正常完成）/ Manual（人工完成）"],
      stateAxes: [
        { label: "订单状态", values: ["未提交", "已提交", "已审核", "部分收货", "已完成"] },
        { label: "完成方式", values: ["未完成", "正常完成", "人工完成"] }
      ],
      flow: [
        { id: "po-create", title: "创建采购单", owner: "采购人员", detail: "人工填写供应商、产品、数量与可选来源生产工单；只有未提交可以修改核心字段。" },
        { id: "po-approve", title: "提交与审核", owner: "采购人员", detail: "未提交经提交、审核后成为已审核；审核本身不自动产生库存。" },
        { id: "po-arrival", title: "到货外观验收与拒收", owner: "仓库人员", detail: "只检查外观、型号与数量，记录到货、收货前拒收与实际接收数量；明显破损或型号错误直接拒收，不进入库存。" },
        { id: "po-receive", title: "确认实际接收", owner: "仓库人员", detail: "只按 received_qty（实际接收数量）增加库存，并将实际接收数量全部放入 QualityHold（质量隔离位）；仓库不在此时判断质检合格数量。" },
        { id: "po-inspection", title: "采购到货质检", owner: "生产质检人员", detail: "检验质量隔离位中的实际接收货物，分别记录 inspected_qty（检验数量）、qualified_qty（质检合格数量）和 unqualified_qty（质检不合格数量）；检验不直接改变库存。" },
        { id: "po-quality", title: "质量处置决定", owner: "生产质检 / 采购", detail: "生产质检人员对质检合格数量决定放行、对质检不合格数量决定报废；采购人员可对质检不合格数量决定并协调退回供应方；决定先进入待执行。" },
        { id: "po-quality-execute", title: "处置实物执行", owner: "仓库人员", detail: "仓库确认放行移位、退回出库或报废扣减；实际库存变化形成流水。" },
        { id: "po-putaway", title: "确认上架", owner: "仓库人员", detail: "从收货暂存位移到目标库位，总库存不重复增加。" },
        { id: "po-complete", title: "完成采购", owner: "采购人员", detail: "全部收货后正常完成，或人工终止剩余未收数量；已收货合格货物仍可上架，质量隔离货物仍须完成处置。" }
      ],
      relations: [
        { direction: "upstream", module: "生产", text: "采购明细可人工关联来源生产工单，不执行自动需求运算。" },
        { direction: "downstream", module: "库存", text: "实际接收、质量处置执行和上架必须通过库存应用服务记账与移位。" },
        { direction: "collaboration", module: "生产质检", text: "生产质检人员执行采购到货检验并决定放行或报废。" },
        { direction: "read", module: "追溯 / 看板", text: "展示到货拒收、实际接收、质量处置、上架和来源关系。" }
      ],
      ownedFacts: ["采购订单与供应商交付意图", "到货拒收、累计实际收货与上架进度", "质量处置决定与执行状态", "采购完成方式与审计"],
      boundaries: ["收货前拒收不进入库存；实际接收才增加企业总库存并全部进入质量隔离位。", "采购到货质检只记录合格/不合格数量，不直接改变库存。", "质量处置决定不直接修改库存，由仓库执行确认形成实物变化。", "上架不重复增加企业总库存。", "人工完成只终止未收货余量，不关闭已存在的上架或质量处置任务。", "一期不自动计算缺口或生成采购建议。"],
      exceptions: ["到货/拒收/接收数量关系或质检合格/不合格数量关系错误", "拒收、报废或人工完成原因缺失", "重复到货验收、质检、处置执行或上架", "非法收货暂存/质量隔离/目标库位", "质量隔离货物参与预留或领料", "处置决定角色或仓库执行角色错误", "跨租户供应商或来源关联"],
      extensions: ["采购建议与 MRP", "供应商绩效", "到货预约", "复杂抽样检验", "应付与发票匹配"],
      specs: [
        { label: "采购订单业务规则", href: "../specs/10-erp-wms/采购订单业务规则.md" },
        { label: "ERP/WMS 领域模型", href: "../specs/10-erp-wms/领域模型.md" },
        { label: "ERP/WMS 接口契约", href: "../specs/10-erp-wms/接口契约.md" }
      ]
    },
    {
      id: "inventory",
      code: "03 / FACT CORE",
      title: "库存",
      page: "库存业务架构.html",
      confidence: "confirmed",
      confidenceLabel: "已确认",
      tagline: "所有数量变化共用的事实内核",
      purpose: "用余额快照、业务预留、库位分配与不可随意修改的流水解释库存是多少、在哪里、为何变化。",
      inputs: ["采购收货与上架命令", "销售履约命令", "生产领退料与成品入库", "调拨与盘点"],
      outputs: ["InventoryBalance", "InventoryReservation", "InventoryReservationAllocation", "InventoryTransaction"],
      roles: ["仓库人员", "业务模块应用服务"],
      states: ["预留：Active（有效）→ PartiallyReleased（部分释放）→ Released（已释放）", "盘点：NotStarted（未盘点）→ Counting（盘点中）→ ConfirmedAdjusted（已确认并调整）"],
      stateAxes: [
        { label: "库存恒等式", values: ["实物库存", "减去预留", "等于可用库存"] },
        { label: "盘点状态", values: ["未盘点", "盘点中", "已确认并调整"] }
      ],
      flow: [
        { id: "inv-validate", title: "校验业务来源", owner: "库存应用服务", detail: "校验租户、产品、库位、数量、来源单据与幂等键。" },
        { id: "inv-lock", title: "锁定事实", owner: "库存应用服务", detail: "使用版本或行锁阻止并发超卖和重复记账。" },
        { id: "inv-change", title: "余额与预留变更", owner: "库存应用服务", detail: "在同一事务中更新实物余额、业务预留和库位级分配，始终保证可用库存不小于零。" },
        { id: "inv-move", title: "库位移动", owner: "库存应用服务", detail: "上架、拣货、退回和调拨只改变受管位置；企业总实物库存保持不变。" },
        { id: "inv-ledger", title: "追加库存流水", owner: "库存应用服务", detail: "记录来源、位置、数量、操作人和发生时间。" },
        { id: "inv-count", title: "盘点并调整", owner: "仓库人员", detail: "未盘点生成范围，盘点中录入实盘数，确认并调整时才按差异生成库存调整流水。" }
      ],
      relations: [
        { direction: "caller", module: "采购 / 销售 / 制造", text: "只能调用库存应用服务，不能直接写余额表。" },
        { direction: "read", module: "追溯 / 看板 / AI", text: "读取受租户与权限过滤的库存事实。" }
      ],
      ownedFacts: ["库位库存余额", "业务预留与库位级分配", "库存变化流水", "调拨和盘点调整事实"],
      boundaries: ["available_qty（可用量）= on_hand_qty（实物量）- reserved_qty（预留量），且不得小于零。", "直接拣货内部自动预留不扣实物、拣货只移动位置、发货才扣企业总实物库存。", "QualityHold（质量隔离位）不得参与正常预留或领料。", "余额是快照，流水与预留说明来源；历史不得被直接覆盖。"],
      exceptions: ["库存不足或版本冲突", "同一幂等键载荷冲突", "库位类型或调拨方向不合法", "来源单据不匹配", "盘点重复确认", "余额、分配和流水事务不一致"],
      extensions: ["复杂批次与序列号", "保质期与 FEFO（先到期先出）", "多仓策略", "循环盘点", "库存重算与对账", "可靠领域事件"],
      specs: [
        { label: "库存业务规则", href: "../specs/10-erp-wms/库存业务规则.md" },
        { label: "ERP/WMS 领域模型", href: "../specs/10-erp-wms/领域模型.md" },
        { label: "ERP/WMS 验收标准", href: "../specs/10-erp-wms/验收标准.md" },
        { label: "ERP/WMS 接口契约", href: "../specs/10-erp-wms/接口契约.md" }
      ]
    },
    {
      id: "manufacturing",
      code: "04 / EXECUTION",
      title: "制造与质量",
      page: "制造质量业务架构.html",
      confidence: "confirmed",
      confidenceLabel: "已确认",
      tagline: "生产意图、现场执行和质量事实分离",
      purpose: "从人工关联的销售来源建立生产工单，记录派工、工序执行、报工、质检以及领退料和成品入库。",
      inputs: ["BOM（物料清单）与工艺路线", "最多一条来源销售订单明细", "人员与可选设备", "仓库库存"],
      outputs: ["经审核的生产工单", "派工与工序执行事实", "报工与质检事实", "成品入库来源"],
      roles: ["生产质检人员", "仓库人员"],
      states: ["WorkOrder（生产工单）：Draft（未提交）→ PendingApproval（待审核）→ Released（已下达）→ InProgress（生产中）→ Completed（已完成）", "审核分支：PendingApproval（待审核）→ Rejected（审核拒绝）→ PendingApproval（重新提交）", "OperationExecution（工序执行）：NotStarted（未开始）→ Running（进行中）↔ Paused（已暂停）→ Completed（已完成）"],
      stateAxes: [
        { label: "工单主状态", values: ["未提交", "待审核", "已下达", "生产中", "已完成"] },
        { label: "审核分支", values: ["待审核", "审核拒绝", "修改并重提"] },
        { label: "完成方式", values: ["未完成", "正常完成", "人工完成"] }
      ],
      flow: [
        { id: "mes-order", title: "创建工单", owner: "生产质检人员", detail: "绑定有效 BOM 和工艺路线；一个工单最多关联一条销售订单明细，同一销售明细可以拆为多个工单，选择来源后默认带出尚未承接数量并允许修改。" },
        { id: "mes-review", title: "提交与审核", owner: "生产质检人员", detail: "未提交进入待审核；通过后成为已下达并锁定 BOM 与工艺版本，拒绝后必须填写原因且允许修改重提。" },
        { id: "mes-material", title: "生产领料", owner: "生产质检人员 + 仓库人员", detail: "生产质检人员创建领料单，仓库人员确认后通过库存服务扣减原料。" },
        { id: "mes-dispatch", title: "派工", owner: "生产质检人员", detail: "表达人员、工序、数量和可选设备安排，不代表现场已经开始。" },
        { id: "mes-execute", title: "工序执行", owner: "生产质检人员", detail: "上一道必需工序完成后才能开始下一道；记录开始、暂停、恢复、完成、人员与可选设备。" },
        { id: "mes-quality", title: "报工与质检", owner: "生产质检人员", detail: "允许分批报工和按批检验；不合格品只能报废或隔离，一期不建立返工流程。" },
        { id: "mes-receipt", title: "入库与完成", owner: "生产质检人员 + 仓库人员", detail: "生产质检人员创建成品入库单，仓库人员确认实物入库；合格且未入库数量可分批入库，人工完成只终止剩余生产且不得补造事实。" }
      ],
      relations: [
        { direction: "upstream", module: "销售", text: "来源销售行只作人工关联、进度汇总和追溯。" },
        { direction: "downstream", module: "库存", text: "领料、退料、成品入库必须穿过库存应用服务。" },
        { direction: "context", module: "IoT", text: "工序执行可选关联设备，不复制遥测和告警事实。" }
      ],
      ownedFacts: ["生产意图、销售来源与计划数量", "审核和派工安排", "工序实际执行", "报工和质量结果", "生产完成方式与库存单据来源"],
      boundaries: ["工单、派工和工序执行不可混为一个状态。", "设备归属保存在设备主数据，当前工单关联保存在工序执行。", "制造不得直接修改 InventoryBalance（库存余额）。", "一期不建设 WIP（在制品库存）、线边仓、APS（高级排产）或返工链路。"],
      exceptions: ["无有效 BOM/工艺路线", "审核拒绝或人工完成原因缺失", "库存不足导致领料失败", "前序工序未完成或累计派工/报工超限", "不合格数量未处置仍尝试完成", "重复领退料、质检或成品入库"],
      extensions: ["WIP 与线边仓", "APS 与产能日历", "返工与复检", "高级质量处置", "设备自动采数", "物料追溯深化"],
      specs: [
        { label: "生产与质检业务规则", href: "../specs/20-mes/生产与质检业务规则.md" },
        { label: "MES 领域模型", href: "../specs/20-mes/领域模型.md" },
        { label: "MES 接口契约", href: "../specs/20-mes/接口契约.md" }
      ]
    },
    {
      id: "iot",
      code: "05 / OT FACT",
      title: "IoT",
      page: "IoT业务架构.html",
      confidence: "confirmed",
      confidenceLabel: "已确认",
      tagline: "设备遥测、状态与告警的独立事实域",
      purpose: "通过 MQTT QoS 1 接入模拟设备，独立保存遥测、状态和告警，再延后关联生产上下文。",
      inputs: ["设备模型与单设备凭证", "MQTT QoS 1（至少送达一次）消息", "可选生产上下文", "告警确认操作"],
      outputs: ["DeviceTelemetry", "DeviceStatus", "DeviceAlarm", "设备业务上下文"],
      roles: ["IoT人员", "模拟设备"],
      states: ["Device（设备）：Active（启用）→ Disabled（停用）", "当前状态：Online（在线）/ Offline（离线），Idle（空闲）/ Running（运行）/ Stopped（停止）", "Alarm（告警）：Triggered（已触发）→ Acked（已确认）→ Recovered（已恢复），或先恢复后补确认"],
      stateAxes: [
        { label: "设备管理", values: ["启用", "停用"] },
        { label: "通信状态", values: ["在线", "默认 60 秒无有效通信", "离线"] },
        { label: "告警过程", values: ["已触发", "已确认或先恢复", "已恢复"] }
      ],
      flow: [
        { id: "iot-auth", title: "设备与凭证", owner: "IoT人员", detail: "设备长期关联工作中心、生产区域和地图点位；每台设备使用独立凭证，明文只展示一次，有历史的设备只能停用。" },
        { id: "iot-validate", title: "消息校验与去重", owner: "IoT", detail: "验证设备、凭证、租户、指标和去重键；相同键同载荷幂等成功，不同载荷冲突，任一字段非法时拒绝整条消息。" },
        { id: "iot-ingest", title: "保存原始遥测", owner: "IoT", detail: "未重复的有效消息先追加 DeviceTelemetry（设备遥测）；延迟数据仍保存，但不得倒退当前状态。" },
        { id: "iot-status", title: "更新状态快照", owner: "IoT", detail: "基于最新有效消息更新在线与运行状态；默认 60 秒无有效通信则离线，可按设备模型配置。" },
        { id: "iot-alarm", title: "生成或恢复告警", owner: "IoT", detail: "一期使用可配置的单指标阈值；同一设备与规则只保留一个活动告警，恢复可以先于人工确认。" },
        { id: "iot-context", title: "补充业务上下文", owner: "IoT人员", detail: "自动判断不可靠或 Core 不可用时延后或人工关联工序/工单。" }
      ],
      relations: [
        { direction: "context", module: "制造", text: "通过软引用关联工序执行和工单，不直接修改 Core 表。" },
        { direction: "read", module: "GIS / 看板 / AI", text: "提供设备、状态和告警只读事实。" }
      ],
      ownedFacts: ["设备身份与接入状态", "原始遥测", "当前设备状态快照", "告警生命周期"],
      boundaries: ["IoT 与 Core 共用同一 PostgreSQL 12.1 实例的 public schema，通过表名前缀、服务访问边界和软引用/受控外键隔离，不直接读写 Core 表。", "设备主数据不保存当前工单；生产关联属于工序执行上下文。", "Core 故障不得导致已接收遥测或告警丢失。", "遥测、状态、告警三类事实不可合并。"],
      exceptions: ["QoS 1 重复投递", "同一去重键不同载荷", "凭证失效或设备停用", "消息指标不在设备模型或类型错误", "延迟数据试图回滚当前状态", "Core 暂不可用或告警上下文无法可靠判断"],
      extensions: ["真实 PLC 接入", "OPC UA / Modbus 适配", "规则引擎", "边缘计算", "三维数字孪生", "设备预测维护"],
      specs: [
        { label: "设备接入与告警业务规则", href: "../specs/30-iot-digital-twin/设备接入与告警业务规则.md" },
        { label: "IoT 领域模型", href: "../specs/30-iot-digital-twin/领域模型.md" },
        { label: "IoT 接口契约", href: "../specs/30-iot-digital-twin/接口契约.md" }
      ]
    },
    {
      id: "gis",
      code: "06 / READ MODEL",
      title: "GIS 与看板",
      page: "GIS看板业务架构.html",
      confidence: "confirmed",
      confidenceLabel: "已确认",
      tagline: "跨域事实的空间化与管理摘要",
      purpose: "以权限约束的只读模型展示厂区、仓库、生产区域、设备点位和跨域业务摘要。",
      inputs: ["库存、订单、制造与质量事实", "设备状态与告警", "多张二维底图与百分比点位", "今日/近 7 天/近 30 天"],
      outputs: ["二维地图", "七组业务摘要", "异常与陈旧数据标识", "权限受控的领域详情跳转"],
      roles: ["租户管理员", "具有地图配置权限的 IoT 人员", "所有授权查询角色"],
      states: ["展示状态优先级：告警 > 离线 > 预警 > 正常", "数据新鲜度：实时 / 已过期 / 暂不可用", "页面只读，无源业务状态迁移"],
      stateAxes: [
        { label: "点位状态", values: ["告警", "离线", "预警", "正常"] },
        { label: "时间范围", values: ["今日（默认）", "近 7 天", "近 30 天"] },
        { label: "刷新", values: ["每 30 秒", "手动刷新", "失败标记已过期"] }
      ],
      flow: [
        { id: "gis-config", title: "配置地图与点位", owner: "租户管理员 / IoT人员", detail: "同租户可维护多张二维地图；仓库、生产区域和设备使用百分比坐标，失效引用标记配置异常。" },
        { id: "gis-filter", title: "解析查询上下文", owner: "GIS / Dashboard", detail: "应用租户、权限和租户时区；时间范围默认今日，也支持近 7 天和近 30 天。" },
        { id: "gis-read", title: "读取领域事实", owner: "只读查询模型", detail: "从授权领域获取库存、履约、生产、质量、设备、告警和追溯七组摘要，不复制源业务状态。" },
        { id: "gis-project", title: "地图与指标投影", owner: "GIS / Dashboard", detail: "按告警、离线、预警、正常优先级展示点位，并组合每组 3～5 个核心指标。" },
        { id: "gis-refresh", title: "刷新与陈旧标识", owner: "Frontend", detail: "默认每 30 秒自动刷新并支持手动刷新；来源失败时保留旧值但显著标记已过期和生成时间。" },
        { id: "gis-drill", title: "下钻源详情", owner: "用户", detail: "点击点位或摘要进入有权访问的源业务详情，目标页面再次校验权限。" }
      ],
      relations: [
        { direction: "read", module: "Core / IoT", text: "仅读取受权限过滤的业务与设备事实。" },
        { direction: "consumer", module: "Frontend", text: "承载地图、看板和异常下钻交互。" }
      ],
      ownedFacts: ["二维底图与地图配置", "百分比空间点位配置", "只读展示查询模型"],
      boundaries: ["地图和看板是两个独立页面，共用查询口径与权限上下文。", "除底图和点位外，不保存订单、库存、制造、质量或设备的第二套事实。", "无权数据直接隐藏，不使用 0 或模糊值替代。", "一期不建设三维数字孪生。"],
      exceptions: ["源数据权限不足", "点位引用缺失或失效", "源模块不可用", "旧值未标记已过期", "统计时间范围或租户时区不一致", "摘要与源详情口径不一致"],
      extensions: ["多层级厂区", "三维场景", "实时流式看板", "可配置指标体系", "自定义布局与公式"],
      specs: [
        { label: "二维地图与综合看板业务规则", href: "../specs/40-gis-dashboard/二维地图与综合看板业务规则.md" },
        { label: "GIS 与看板领域模型", href: "../specs/40-gis-dashboard/领域模型.md" },
        { label: "GIS 与看板接口契约", href: "../specs/40-gis-dashboard/接口契约.md" }
      ]
    },
    {
      id: "ai",
      code: "07 / QUERY",
      title: "AI 助手",
      page: "AI助手业务架构.html",
      confidence: "confirmed",
      confidenceLabel: "已确认",
      tagline: "受权限约束、可审计的只读查询入口",
      purpose: "通过受控只读工具查询业务事实，再经 PokeAPI 中转站调用 Grok 模型生成带依据、时间范围和审计标识的回答。",
      inputs: ["用户问题与最小页面上下文", "租户与权限上下文", "受控只读工具", "PokeAPI 与 Grok 部署配置"],
      outputs: ["自然语言回答或部分结果", "来源与时间范围", "工具/请求/实际模型编号", "会话与调用审计"],
      roles: ["所有授权业务角色"],
      states: ["只读会话，不迁移业务状态", "调用结果：Success（成功）/ Partial（部分结果）/ Failed（失败）/ Rejected（拒绝）"],
      stateAxes: [
        { label: "权限", values: ["普通用户权限", "服务端工具复核", "授权事实"] },
        { label: "调用结果", values: ["成功", "部分结果", "失败或拒绝"] },
        { label: "模型边界", values: ["PokeAPI", "Grok 模型", "只读回答"] }
      ],
      flow: [
        { id: "ai-question", title: "接收问题", owner: "AI 助手", detail: "保留当前用户、租户、会话和请求编号；页面只传业务对象类型与编号，不发送整个页面。" },
        { id: "ai-plan", title: "选择受控工具", owner: "AI 助手", detail: "只允许缩小权限，不能扩大数据范围，也不能调用未注册工具。" },
        { id: "ai-query", title: "执行只读查询", owner: "领域查询工具", detail: "服务端再次校验租户、用户、数据范围、时间范围和数量上限；不直连数据库或执行 SQL。" },
        { id: "ai-model", title: "调用 Grok 模型", owner: "AI 服务", detail: "通过 https://www.poke2api.com 的 OpenAI Responses（响应式接口）兼容协议调用 Grok，默认优先 grok-4.6，实际模型编号由部署配置决定。" },
        { id: "ai-answer", title: "生成可核对回答", owner: "AI 助手", detail: "展示数据来源、统计时间、工具摘要、请求编号和实际模型编号；部分来源失败时保留已取得事实并标记缺失。" },
        { id: "ai-audit", title: "记录调用审计", owner: "AI", detail: "成功、失败、超时和拒绝均记录审计，不保存密码、设备凭证、中转站密钥或无关敏感数据。" }
      ],
      relations: [
        { direction: "security", module: "Auth", text: "沿用普通用户权限，不成为超级管理员。" },
        { direction: "read", module: "领域查询", text: "通过受控只读工具访问，不生成 SQL 直连业务库。" },
        { direction: "external", module: "PokeAPI / Grok", text: "只发送回答所需的最小授权业务摘要，密钥仅从安全环境读取。" }
      ],
      ownedFacts: ["对话上下文", "工具调用审计", "回答来源与实际模型摘要"],
      boundaries: ["一期不修改任何业务数据，即使用户在对话中同意也不能写入。", "工具白名单只能缩小用户权限。", "不直接生成 SQL（数据库查询语句），不建立第二套业务事实。", "一期不启用 Grok 联网搜索或 X 搜索。"],
      exceptions: ["无权限或跨租户查询", "工具调用超时或部分来源失败", "PokeAPI 不可用、模型不可用或额度不足", "来源数据不足或模型回答与工具结果不一致", "敏感字段泄露或密钥误入日志"],
      extensions: ["RAG（先检索企业文档再回答）与知识库", "文档解析", "向量检索", "多步分析工作流", "需审批的业务建议", "可观测性与质量评估"],
      specs: [
        { label: "AI 助手业务规则", href: "../specs/50-ai-assistant/AI助手业务规则.md" },
        { label: "AI 助手领域模型", href: "../specs/50-ai-assistant/领域模型.md" },
        { label: "AI 助手接口契约", href: "../specs/50-ai-assistant/接口契约.md" }
      ]
    }
  ],
  goldenFlow: [
    {
      id: "g-sales", index: "01", title: "销售需求", module: "sales", fact: "销售订单行",
      detail: "销售人员记录客户、产品、数量和计划交期，形成后续生产与仓储履约的需求来源。",
      roles: ["销售人员"], inputs: ["客户与商品主数据", "客户需求与计划交期"], outputs: ["销售订单及订单明细"],
      states: ["未提交 → 已提交 → 已审核"], exceptions: ["无效客户/商品", "跨租户引用", "非法状态修改"], sources: ["销售订单业务规则"]
    },
    {
      id: "g-work-order", index: "02", title: "关联生产工单", module: "manufacturing", fact: "销售来源与生产意图",
      detail: "生产质检人员创建生产工单并选择最多一条来源销售订单明细；同一销售明细可以拆为多个工单。",
      roles: ["生产质检人员"], inputs: ["已审核销售订单明细", "BOM（物料清单）", "工艺路线"], outputs: ["带销售来源的生产工单"],
      states: ["生产工单：未提交"], exceptions: ["来源销售明细无效", "BOM 或工艺路线未启用", "跨租户关联"], sources: ["生产与质检业务规则"]
    },
    {
      id: "g-purchase", index: "03", title: "关联采购来源", module: "purchasing", fact: "外部供应意图",
      detail: "采购人员创建采购订单并人工关联来源生产工单；仓库人员不负责判断采购来源。",
      roles: ["采购人员"], inputs: ["已确认的原料采购需求", "来源生产工单", "供应商与商品主数据"], outputs: ["带生产来源的采购订单明细"],
      states: ["采购订单：未提交 → 已提交 → 已审核"], exceptions: ["来源工单无效", "供应商或商品不可用", "跨租户关联"], sources: ["采购订单业务规则"]
    },
    {
      id: "g-arrival", index: "04", title: "外观验收与实际接收", module: "purchasing", fact: "到货拒收与接收事实",
      detail: "仓库人员只检查外观、型号和数量，记录收货前拒收与实际接收数量；拒收不入库，实际接收数量全部进入质量隔离位。",
      roles: ["仓库人员"], inputs: ["已审核采购订单", "供应方实际到货", "QualityHold（质量隔离位）"], outputs: ["到货拒收记录", "采购收货记录", "接收数量对应库存流水"],
      states: ["收货单：未确认 → 已确认"], exceptions: ["到货数量关系错误", "拒收原因缺失", "本次到货超过当前待收数量", "重复确认"], sources: ["采购订单业务规则", "ERP/WMS 领域模型"]
    },
    {
      id: "g-purchase-inspection", index: "05", title: "采购到货质量检验", module: "purchasing", fact: "采购质量结论",
      detail: "生产质检人员检验已经接收的待检货物并记录结论；检验结论本身不直接移动或扣减库存。",
      roles: ["生产质检人员"], inputs: ["QualityHold（质量隔离位）中的全部实际接收货物", "检验标准"], outputs: ["质检合格数量", "质检不合格数量"],
      states: ["待检 → PendingDecision（待决定）"], exceptions: ["检验数量超过实际接收未检数量", "检验数量不等于合格与不合格数量之和", "货物或检验标准不匹配", "重复记录结论"], sources: ["采购订单业务规则", "ERP/WMS API 契约"]
    },
    {
      id: "g-purchase-disposition", index: "06", title: "质量处置决定", module: "purchasing", fact: "待执行处置决定",
      detail: "生产质检人员对质检合格数量决定放行、对质检不合格数量决定报废；采购人员可对质检不合格数量决定并协调退回供应方。决定先保存为待执行，不提前改变库存。",
      roles: ["生产质检人员：决定放行或报废", "采购人员：决定并协调退回供应方"], inputs: ["采购质量结论", "供应方沟通结果"], outputs: ["放行/退回/报废处置决定"],
      states: ["PendingDecision（待决定）→ PendingExecution（待执行）"], exceptions: ["角色与处置类型不匹配", "处置数量超限", "退回/报废原因缺失"], sources: ["采购订单业务规则", "ERP/WMS API 契约"]
    },
    {
      id: "g-purchase-execution", index: "07", title: "处置执行与上架", module: "purchasing", fact: "库存移位或扣减事实",
      detail: "仓库人员确认质量处置实物执行；放行移至收货暂存位后再上架，退回或报废从质量隔离位扣减。",
      roles: ["仓库人员"], inputs: ["待执行质量处置决定", "目标收货暂存位/存储位"], outputs: ["处置执行记录", "库存移动或减少流水", "上架结果"],
      states: ["处置：待执行 → 已完成", "上架：待处理 → 处理中 → 已确认"], exceptions: ["无待执行决定", "库位类型错误", "重复执行或重复上架"], sources: ["采购订单业务规则", "ERP/WMS API 契约"]
    },
    {
      id: "g-work-order-review", index: "08", title: "工单审核与下达", module: "manufacturing", fact: "已下达生产意图",
      detail: "生产质检人员提交并审核工单；通过后锁定 BOM（物料清单）与工艺路线版本，拒绝后可修改重提。",
      roles: ["生产质检人员"], inputs: ["未提交生产工单", "有效 BOM（物料清单）与工艺路线"], outputs: ["已下达生产工单或审核拒绝记录"],
      states: ["未提交 → 待审核 → 已下达", "待审核 → 审核拒绝 → 修改并重提"], exceptions: ["审核状态不合法", "BOM 或工艺路线失效", "审核拒绝原因缺失"], sources: ["生产与质检业务规则"]
    },
    {
      id: "g-material", index: "09", title: "生产领料", module: "manufacturing", fact: "原料库存减少",
      detail: "生产质检人员创建领料单，仓库人员核对并确认发料；确认后才通过库存服务扣减原料。",
      roles: ["生产质检人员：创建领料单", "仓库人员：确认发料"], inputs: ["已下达生产工单", "BOM（物料清单）需求", "可用原料库存"], outputs: ["领料事实", "原料库存减少流水"],
      states: ["领料单：待确认 → 已确认"], exceptions: ["领料数量超限", "可用库存不足", "重复确认"], sources: ["生产与质检业务规则", "库存业务规则"]
    },
    {
      id: "g-execution", index: "10", title: "派工与工序执行", module: "manufacturing", fact: "工序执行事实",
      detail: "生产质检人员完成人员、工序、数量和可选设备派工，并记录开始、暂停、恢复与完成。",
      roles: ["生产质检人员"], inputs: ["已下达生产工单", "固定且可选的有效工艺路线", "人员与可选设备"], outputs: ["派工单", "工序执行记录"],
      states: ["未开始 → 进行中 ↔ 已暂停 → 已完成"], exceptions: ["前置工序未完成", "人员/设备不可用", "非法执行状态迁移"], sources: ["生产与质检业务规则"]
    },
    {
      id: "g-iot", index: "11", title: "设备遥测与告警", module: "iot", fact: "遥测、状态与告警",
      detail: "模拟设备上报数据；IoT 人员维护设备并确认告警，IoT 服务先独立保存设备事实，再补充工序上下文。",
      roles: ["IoT 人员", "模拟设备"], inputs: ["MQTT（消息队列遥测传输协议）消息", "设备凭证与告警规则"], outputs: ["遥测记录", "设备状态", "设备告警及生产上下文"],
      states: ["设备：在线/离线", "告警：活动 → 已确认 → 已恢复"], exceptions: ["重复消息", "设备离线", "告警上下文暂时无法关联"], sources: ["设备接入与告警业务规则"]
    },
    {
      id: "g-quality", index: "12", title: "报工与生产质检", module: "manufacturing", fact: "报工与质量结果",
      detail: "生产质检人员分批报工并检验；合格数量进入可入库范围，不合格数量只能隔离或报废。",
      roles: ["生产质检人员"], inputs: ["已完成或可报工的工序执行", "报工数量", "检验标准"], outputs: ["报工记录", "生产质量检验记录", "合格可入库数量"],
      states: ["待报工 → 已报工 → 已检验"], exceptions: ["报工数量超限", "检验数量与报工不一致", "一期不允许返工"], sources: ["生产与质检业务规则"]
    },
    {
      id: "g-finished", index: "13", title: "成品入库", module: "manufacturing", fact: "成品库存增加",
      detail: "生产质检人员创建成品入库单，仓库人员确认实物入库；只按合格且未入库数量增加成品库存。",
      roles: ["生产质检人员：创建入库单", "仓库人员：确认实物入库"], inputs: ["合格且未入库数量", "目标成品库位"], outputs: ["成品入库记录", "成品库存增加流水"],
      states: ["成品入库单：待确认 → 已确认"], exceptions: ["入库数量超过合格未入库数量", "目标库位不合法", "重复确认"], sources: ["生产与质检业务规则", "库存业务规则"]
    },
    {
      id: "g-outbound", index: "14", title: "销售直接拣货与发货", module: "sales", fact: "分批交付与库存扣减",
      detail: "仓库人员按已审核销售订单直接拣货，系统在同一事务内自动预留本次数量并完成移位；发货才扣减企业总实物库存并释放对应预留。",
      roles: ["仓库人员"], inputs: ["已审核销售订单", "可用成品库存", "ShippingStaging（发货暂存位）"], outputs: ["自动预留记录", "拣货移动", "发货与库存扣减事实"],
      states: ["履约进度：未开始 → 进行中 → 全部发货"], exceptions: ["可用库存不足", "履约数量超限", "未发货暂存数量未处理", "重复命令"], sources: ["销售订单业务规则", "库存业务规则"]
    },
    {
      id: "g-observe", index: "15", title: "地图与综合看板", module: "gis", fact: "空间与指标只读聚合",
      detail: "具有权限的业务用户查看二维位置与跨模块摘要；租户管理员维护必要配置，不生成第二套业务事实。",
      roles: ["具有相应查询权限的业务用户", "租户管理员：必要配置"], inputs: ["库存、订单、生产、设备和告警只读投影"], outputs: ["地图点位", "业务指标与异常摘要"],
      states: ["展示状态：告警 > 离线 > 预警 > 正常"], exceptions: ["来源数据失败时保留旧值并标记陈旧", "无权限数据不可见"], sources: ["二维地图与综合看板业务规则"]
    },
    {
      id: "g-ai", index: "16", title: "AI 只读查询", module: "ai", fact: "带来源与审计的解释",
      detail: "具有 AI 查询权限的业务用户通过受控只读工具查询事实；回答保留来源与工具审计，模型不能修改业务数据。",
      roles: ["具有 AI 查询权限的业务用户"], inputs: ["用户问题", "权限过滤后的只读工具结果"], outputs: ["带来源摘要的回答", "工具调用审计"],
      states: ["会话请求：处理中 → 成功/部分成功/失败"], exceptions: ["模型或中转站不可用", "来源不足", "越权或跨租户查询"], sources: ["AI 助手业务规则"]
    }
  ],
  topology: {
    services: [
      {
        id: "frontend", title: "Frontend", port: "Vite", detail: "业务页面、业务架构、地图、看板和 AI 交互。",
        roles: ["授权业务用户"], inputs: ["用户操作", "Gateway 返回的受权数据"], outputs: ["业务命令", "只读查询"],
        states: ["页面本地交互状态"], exceptions: ["不得把前端隐藏按钮当作最终授权"], sources: ["项目架构设计", "原型与交互说明"]
      },
      {
        id: "gateway", title: "Gateway", port: "20001", detail: "统一入口，并分别路由到 Auth、Core 或 IoT。",
        roles: ["系统入口"], inputs: ["Frontend 请求", "Token 与权限元数据"], outputs: ["Auth / Core / IoT 路由请求", "统一异常响应"],
        states: ["无领域状态"], exceptions: ["不执行领域业务逻辑", "不把 IoT 请求转发给 Core"], sources: ["项目架构设计 / 服务边界"]
      },
      {
        id: "auth", title: "Auth", port: "10002", detail: "用户、角色、权限、租户上下文和 Token 签发。",
        roles: ["租户管理员", "授权用户"], inputs: ["登录与权限管理请求"], outputs: ["受信任身份与租户上下文", "Token"],
        states: ["当前会话与权限事实"], exceptions: ["当前仅为框架骨架，不表示鉴权已实现"], sources: ["项目架构设计 / 认证与授权边界"]
      },
      {
        id: "core", title: "Core", port: "10003", detail: "采购、销售、库存、制造、质量、追溯、统计与 AI 查询。",
        roles: ["采购、销售、仓库、生产质检人员"], inputs: ["Gateway 路由的业务命令与查询"], outputs: ["业务单据", "库存与制造事实", "只读投影"],
        states: ["各逻辑模块拥有独立显式状态"], exceptions: ["跨模块只能调用应用服务", "禁止直接修改其他模块数据"], sources: ["项目架构设计 / Core 逻辑模块"]
      },
      {
        id: "iot-service", title: "IoT", port: "10004", detail: "设备、凭证、MQTT、遥测、状态与告警。",
        roles: ["IoT 人员", "模拟设备"], inputs: ["Gateway 管控请求", "MQTT QoS 1 消息"], outputs: ["设备遥测、状态与告警事实"],
        states: ["设备状态", "告警生命周期"], exceptions: ["Core 故障不得导致已接收设备事实丢失", "IoT 管控请求不经过 Core"], sources: ["项目架构设计", "IoT 领域模型"]
      }
    ],
    coreModules: ["purchasing", "sales", "inventory", "manufacturing", "quality", "traceability", "dashboard", "ai"]
  },
  factFlow: [
    { layer: "业务意图", items: ["销售订单", "采购订单", "生产工单"], note: "说明要做什么、为何做。" },
    { layer: "执行单据", items: ["收货 / 上架", "拣货 / 发货", "派工 / 领退料"], note: "控制具体业务动作。" },
    { layer: "可信事实", items: ["库存流水与预留", "工序执行与质检", "遥测、状态与告警"], note: "记录实际发生了什么。" },
    { layer: "只读投影", items: ["追溯", "GIS", "看板", "AI 助手"], note: "解释事实，不建立第二套事实。" }
  ],
  boundaries: [
    { title: "库存唯一写入口", owner: "Inventory", rule: "采购、销售和制造只能调用库存应用服务，禁止直接修改 InventoryBalance。" },
    { title: "设备事实独立", owner: "IoT", rule: "IoT 先独立保存遥测与告警；Core 故障不回滚已接收设备事实。" },
    { title: "身份与租户可信", owner: "Auth / Gateway", rule: "tenant_id、账号与 jti 来自受信任上下文，前端不得指定。" },
    { title: "人工供需关联", owner: "Sales / Manufacturing / Purchasing", rule: "一个工单最多关联一条销售明细，同一销售明细可拆为多个工单；一期不执行自动 MRP。" },
    { title: "完成不伪造事实", owner: "Sales / Purchasing / Manufacturing", rule: "人工完成只终止剩余履约，已经发生的数量、质量、库存与执行历史继续保留。" },
    { title: "展示层只读", owner: "Traceability / GIS / Dashboard", rule: "只读投影不得修改或复制源领域状态，来源失败必须标记陈旧或不可用。" },
    { title: "AI 外部处理边界", owner: "AI / PokeAPI", rule: "仅发送最小授权摘要；AI 不直连数据库、不写业务数据、不启用 Grok 联网或 X 搜索。" }
  ],
  roadmap: [
    { phase: "第 1 周", title: "底座与库存内核", detail: "认证租户、主数据、余额、预留、流水、事务与并发。" },
    { phase: "第 2 周", title: "采购销售仓储", detail: "收货上架、分批履约、人工供需关联。" },
    { phase: "第 3 周", title: "制造与 IoT", detail: "生产执行、领退料、质检、成品入库、设备事实。" },
    { phase: "第 4 周", title: "追溯与轻量体验", detail: "跨域追溯、GIS、看板、AI 只读查询和验收。" },
    { phase: "未来", title: "计划与商业扩展", detail: "MRP/APS、复杂批次、财务、退货、可靠事件与三维场景。" }
  ]
};
