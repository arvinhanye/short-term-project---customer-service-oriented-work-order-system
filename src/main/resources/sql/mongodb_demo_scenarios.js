const database = db.getSiblingDB("ticket_management_logs");
const seedBatch = "three-role-demo-v1";

database.action_logs.createIndex({ seed_key: 1 }, { unique: true, sparse: true });
database.system_logs.createIndex({ seed_key: 1 }, { unique: true, sparse: true });

const scenarios = [
  "登录后频繁被要求重新验证身份",
  "退款已审核但银行卡仍未到账",
  "同一订单出现两笔扣款记录",
  "移动端提交按钮点击后无响应",
  "报表导出后金额列显示为空",
  "修改收货地址后订单没有同步",
  "电子发票抬头保存失败",
  "优惠券显示可用但结算失败",
  "夜间访问订单页面偶发 502",
  "订单状态长时间停留在处理中",
  "账户绑定手机号无法更换",
  "报销附件上传后预览模糊"
];
const priorities = ["LOW", "MEDIUM", "HIGH", "URGENT"];
const channels = ["DESKTOP", "EMAIL", "PHONE"];

function itemIdFor(index) {
  return String(70000 + index);
}

function createdAtFor(index, hour = 0) {
  return new Date(Date.UTC(2026, 4, 1 + index, 0 + hour, 30, 0));
}

function currentAdminFor(index) {
  if ((index - 1) % 10 === 0) return null;
  if (index % 20 === 12) return "10003";
  return "19002";
}

function pendingTransferFor(index, itemId) {
  const requestId = `e${itemId.padStart(7, "0")}-0000-4000-8000-${itemId.padStart(12, "0")}`;
  if (index % 20 === 2) {
    return {
      requestId,
      requestedBy: "19002",
      target: "10003",
      reason: "需要支付渠道经验，请二线管理员确认接手"
    };
  }
  if (index % 20 === 12) {
    return {
      requestId,
      requestedBy: "10003",
      target: "19002",
      reason: "客户已补充完整材料，邀请演示管理员继续处理"
    };
  }
  return null;
}

for (let index = 1; index <= 60; index += 1) {
  const itemId = itemIdFor(index);
  const scenario = scenarios[(index - 1) % scenarios.length];
  const status = (index - 1) % 5;
  const assignedAdmin = currentAdminFor(index);
  const transfer = pendingTransferFor(index, itemId);
  const reminderCount = status === 0 || status === 1 ? index % 3 : 0;
  const createdAt = createdAtFor(index);
  const lastProcessedAt = createdAtFor(index, 10 + (index % 8));

  database.item_details.updateOne(
    { item_id: itemId },
    {
      $set: {
        item_id: itemId,
        description: `${scenario}。用户在桌面客户端完成操作后可稳定复现，已提供发生时间、操作步骤和相关订单信息。` +
          `这是第 ${String(index).padStart(2, "0")} 条演示数据，用于验证列表筛选、详情展示和完整历史时间线。`,
        images: index % 4 === 0
          ? [`/attachments/${itemId}/problem-screen.png`, `/attachments/${itemId}/order-proof.png`]
          : [],
        metadata: {
          language: "zh-CN",
          priority: priorities[(index - 1) % priorities.length],
          created_by_user_id: "19003",
          assigned_admin_id: assignedAdmin,
          transfer_request_id: transfer?.requestId ?? null,
          transfer_requested_by_admin_id: transfer?.requestedBy ?? null,
          transfer_target_admin_id: transfer?.target ?? null,
          transfer_reason: transfer?.reason ?? null,
          transfer_requested_at: transfer ? createdAtFor(index, 30) : null,
          reminder_count: reminderCount,
          last_reminded_at: reminderCount > 0 ? createdAtFor(index, 48) : null,
          contact_channel: channels[(index - 1) % channels.length],
          last_processed_at: lastProcessedAt
        },
        seed_batch: seedBatch
      }
    },
    { upsert: true }
  );

  database.comments.updateOne(
    { event_id: `demo-comment-user-${itemId}` },
    {
      $set: {
        event_id: `demo-comment-user-${itemId}`,
        user_id: "19003",
        item_id: itemId,
        content: index % 3 === 0
          ? "补充说明：该问题在公司网络和家庭网络下都能复现，已确认不是浏览器缓存导致。"
          : "我已按客服建议重新操作并补充相关订单信息，请协助确认下一步处理时间。",
        rating: "",
        tags: ["CUSTOMER_REPLY"],
        created_at: createdAtFor(index, 5),
        seed_batch: seedBatch
      }
    },
    { upsert: true }
  );

  if (assignedAdmin !== null) {
    database.comments.updateOne(
      { event_id: `demo-comment-admin-${itemId}` },
      {
        $set: {
          event_id: `demo-comment-admin-${itemId}`,
          user_id: assignedAdmin,
          item_id: itemId,
          content: index % 4 === 0
            ? "已收到材料，正在联系对应业务渠道核对流水，预计一个工作日内更新结果。"
            : "问题已登记并完成初步复现，我们会按优先级继续处理，有进展会在工单中同步。",
          rating: "",
          tags: ["ADMIN_REPLY"],
          created_at: createdAtFor(index, 7),
          seed_batch: seedBatch
        }
      },
      { upsert: true }
    );
  }

  if (assignedAdmin === "19002" && index % 2 === 0) {
    database.comments.updateOne(
      { event_id: `demo-note-admin-${itemId}` },
      {
        $set: {
          event_id: `demo-note-admin-${itemId}`,
          user_id: "19002",
          item_id: itemId,
          content: index % 6 === 0
            ? "内部备注：该问题与本周已知故障特征一致，关联变更单 CHG-2026-0715，需持续观察。"
            : "内部备注：已核对用户材料和关联订单，证据完整，可按标准流程继续处理。",
          rating: "",
          tags: ["INTERNAL_NOTE"],
          created_at: createdAtFor(index, 8),
          seed_batch: seedBatch
        }
      },
      { upsert: true }
    );
  }

  if (status === 2 || status === 3) {
    const rating = String((index % 5) + 1);
    database.comments.updateOne(
      { event_id: `demo-rating-user-${itemId}` },
      {
        $set: {
          event_id: `demo-rating-user-${itemId}`,
          user_id: "19003",
          item_id: itemId,
          content: Number(rating) >= 4
            ? "处理过程清晰，回复及时，问题已经解决。"
            : Number(rating) === 3
              ? "问题已解决，希望后续能更快同步处理进度。"
              : "最终问题已处理，但等待时间较长，希望优化响应效率。",
          rating,
          tags: ["CUSTOMER_RATING"],
          created_at: createdAtFor(index, 20),
          seed_batch: seedBatch
        }
      },
      { upsert: true }
    );
  }
}

const accounts = [
  {
    id: "19001",
    role: "ROOT",
    actions: ["VIEW_AUDIT", "MANAGE_ADMIN", "ROLE_REVIEW", "PASSWORD_RESET", "DISABLE_ACCOUNT", "EXPORT_AUDIT"],
    logTypes: ["ADMIN_OPERATION", "USER_DISABLED", "LOGIN", "LOGIN_FAIL", "SYSTEM_STARTUP", "PASSWORD_RESET"]
  },
  {
    id: "19002",
    role: "ADMIN",
    actions: ["VIEW", "SEARCH", "CLAIM_TICKET", "ADD_COMMENT", "CHANGE_STATUS", "REQUEST_TICKET_TRANSFER"],
    logTypes: ["TICKET_OPERATION", "STATUS_CHANGE", "ADMIN_OPERATION", "TICKET_ASSIGNMENT", "LOGIN", "TICKET_REMINDER"]
  },
  {
    id: "19003",
    role: "USER",
    actions: ["CREATE_ITEM", "VIEW", "SEARCH", "ADD_COMMENT", "URGE_TICKET", "RATE"],
    logTypes: ["LOGIN", "LOGIN_FAIL", "TICKET_OPERATION", "TICKET_REMINDER", "PROFILE_UPDATE", "USER_OPERATION"]
  }
];

accounts.forEach((account, accountIndex) => {
  for (let index = 1; index <= 60; index += 1) {
    const itemId = itemIdFor(index);
    const action = account.actions[(index - 1) % account.actions.length];
    const actionTime = new Date(Date.UTC(2026, 6, 1 + ((index - 1) % 14), accountIndex * 2 + 1, index % 60, 0));
    database.action_logs.updateOne(
      { seed_key: `${seedBatch}-action-${account.id}-${index}` },
      {
        $set: {
          seed_key: `${seedBatch}-action-${account.id}-${index}`,
          seed_batch: seedBatch,
          user_id: account.id,
          item_id: account.role === "ROOT" ? null : itemId,
          action_type: action,
          duration_seconds: String(20 + ((index * 17) % 240)),
          client_info: {
            client_type: "SWING",
            ip: `192.168.10.${20 + accountIndex}`,
            instance_id: `DEMO-${account.role}-01`
          },
          created_at: actionTime
        }
      },
      { upsert: true }
    );

    const logType = account.logTypes[(index - 1) % account.logTypes.length];
    const level = index % 10 === 0 ? "ERROR" : index % 4 === 0 ? "WARN" : "INFO";
    const logTime = new Date(Date.UTC(2026, 6, 1 + ((index - 1) % 14), accountIndex * 2 + 2, index % 60, 0));
    database.system_logs.updateOne(
      { seed_key: `${seedBatch}-system-${account.id}-${index}` },
      {
        $set: {
          seed_key: `${seedBatch}-system-${account.id}-${index}`,
          seed_batch: seedBatch,
          user_id: account.id,
          log_type: logType,
          log_level: level,
          message: account.role === "ROOT"
            ? `治理演示：${logType}，已记录操作者、目标账号、角色变化和原因。`
            : account.role === "ADMIN"
              ? `协作演示：${logType}，关联工单 ${itemId}，处理结果已进入完整时间线。`
              : `客户操作演示：${logType}，关联工单 ${itemId}，客户端反馈正常。`,
          action_detail: {
            ip: `192.168.10.${20 + accountIndex}`,
            operation: logType,
            target_user_id: account.role === "ROOT" ? String(19002 + (index % 2)) : null,
            before_role: account.role === "ROOT" && index % 3 === 0 ? "USER" : null,
            after_role: account.role === "ROOT" && index % 3 === 0 ? "ADMIN" : null,
            reason: account.role === "ROOT" ? "课程演示环境的权限治理与连续性验证" : null
          },
          timestamp: logTime
        }
      },
      { upsert: true }
    );
  }
});

printjson({
  seed_batch: seedBatch,
  item_details: database.item_details.countDocuments({ seed_batch: seedBatch }),
  user_comments: database.comments.countDocuments({ seed_batch: seedBatch, user_id: "19003" }),
  admin_comments: database.comments.countDocuments({ seed_batch: seedBatch, user_id: "19002" }),
  root_actions: database.action_logs.countDocuments({ seed_batch: seedBatch, user_id: "19001" }),
  admin_actions: database.action_logs.countDocuments({ seed_batch: seedBatch, user_id: "19002" }),
  user_actions: database.action_logs.countDocuments({ seed_batch: seedBatch, user_id: "19003" }),
  root_system_logs: database.system_logs.countDocuments({ seed_batch: seedBatch, user_id: "19001" }),
  admin_system_logs: database.system_logs.countDocuments({ seed_batch: seedBatch, user_id: "19002" }),
  user_system_logs: database.system_logs.countDocuments({ seed_batch: seedBatch, user_id: "19003" })
});
