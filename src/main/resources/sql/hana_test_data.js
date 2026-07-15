const database = db.getSiblingDB("ticket_management_logs");

const hanaUserId = "10011";
const adminUserId = "10001";

const tickets = [
  {
    itemId: "9101",
    priority: "HIGH",
    assignedAdminId: null,
    description: "登录时验证码一直收不到，已确认手机号可正常接收短信，需要测试待处理状态、标题关键词和高优先级展示。"
  },
  {
    itemId: "9102",
    priority: "MEDIUM",
    assignedAdminId: adminUserId,
    description: "实名认证资料已经提交，但页面停留在审核中，需要测试处理中状态和客服回复时间线。"
  },
  {
    itemId: "9103",
    priority: "URGENT",
    assignedAdminId: adminUserId,
    description: "退款申请显示已完成，但银行卡未到账，需要测试已完成状态、金额展示和用户评价。"
  },
  {
    itemId: "9104",
    priority: "HIGH",
    assignedAdminId: adminUserId,
    description: "同一订单被扣款两次，希望确认多扣金额是否可退回，需要测试已关闭状态。"
  },
  {
    itemId: "9105",
    priority: "LOW",
    assignedAdminId: null,
    description: "表单提交后出现页面错误，用户已取消继续处理，需要测试已取消状态。"
  },
  {
    itemId: "9106",
    priority: "MEDIUM",
    assignedAdminId: null,
    description: "点击导出按钮没有反应，需要补充浏览器环境和复现步骤，测试待处理分页。"
  },
  {
    itemId: "9107",
    priority: "HIGH",
    assignedAdminId: adminUserId,
    description: "支付完成后订单状态仍显示待支付，需要测试处理中状态和搜索关键词“订单”。"
  },
  {
    itemId: "9108",
    priority: "URGENT",
    assignedAdminId: adminUserId,
    description: "支付成功后发票未生成，需要测试已完成状态、金额排序和评价记录。"
  }
];

tickets.forEach((ticket, index) => {
  database.item_details.updateOne(
    { item_id: ticket.itemId },
    {
      $set: {
        item_id: ticket.itemId,
        description: ticket.description,
        images: [],
        metadata: {
          language: "zh-CN",
          priority: ticket.priority,
          created_by_user_id: hanaUserId,
          assigned_admin_id: ticket.assignedAdminId,
          transfer_requested_by_admin_id: null,
          transfer_target_admin_id: null,
          transfer_reason: null,
          transfer_requested_at: null,
          reminder_count: 0,
          last_reminded_at: null,
          contact_channel: "DESKTOP",
          last_processed_at: new Date(`2026-07-${String(index + 1).padStart(2, "0")}T08:00:00Z`)
        }
      }
    },
    { upsert: true }
  );

  database.comments.updateOne(
    { item_id: ticket.itemId, tags: ["CUSTOMER_REPLY"], user_id: hanaUserId },
    {
      $set: {
        user_id: hanaUserId,
        item_id: ticket.itemId,
        content: `Hana补充：这是 ${ticket.itemId} 的用户侧补充说明。`,
        rating: "",
        tags: ["CUSTOMER_REPLY"],
        created_at: new Date(`2026-07-${String(index + 1).padStart(2, "0")}T02:30:00Z`)
      }
    },
    { upsert: true }
  );

  if (ticket.assignedAdminId) {
    database.comments.updateOne(
      { item_id: ticket.itemId, tags: ["ADMIN_REPLY"], user_id: adminUserId },
      {
        $set: {
          user_id: adminUserId,
          item_id: ticket.itemId,
          content: `客服回复：已收到 Hana 关于 ${ticket.itemId} 的反馈，正在跟进处理。`,
          rating: "",
          tags: ["ADMIN_REPLY"],
          created_at: new Date(`2026-07-${String(index + 1).padStart(2, "0")}T03:10:00Z`)
        }
      },
      { upsert: true }
    );
  }
});

[
  { itemId: "9103", rating: "5", content: "处理结果清楚，退款说明完整。" },
  { itemId: "9108", rating: "4", content: "发票问题已解决，希望后续能更快通知。" }
].forEach((rating) => {
  database.comments.updateOne(
    { item_id: rating.itemId, tags: ["CUSTOMER_RATING"], user_id: hanaUserId },
    {
      $set: {
        user_id: hanaUserId,
        item_id: rating.itemId,
        content: rating.content,
        rating: rating.rating,
        tags: ["CUSTOMER_RATING"],
        created_at: new Date("2026-07-09T02:00:00Z")
      }
    },
    { upsert: true }
  );
});
