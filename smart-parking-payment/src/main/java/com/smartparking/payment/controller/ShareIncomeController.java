package com.smartparking.payment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.payment.mapper.ShareIncomeMapper;
import com.smartparking.payment.po.ShareIncome;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 车位共享收益（业主端）。
 *
 * <p>业主把闲置的固定车位设为共享后，他人临停产生的费用按 {@code owner_ratio}
 * （默认 70%）分给业主，余下归物业。此前只有 {@code share_income} 表和写入逻辑，
 * 没有任何对外查询接口，业主看不到自己赚了多少钱——本控制器补上这一环。</p>
 */
@Tag(name = "共享收益", description = "业主查看车位共享分润明细")
@RestController
@RequestMapping("/api/payment/share-income")
@RequiredArgsConstructor
public class ShareIncomeController {

    private final ShareIncomeMapper shareIncomeMapper;

    @Operation(summary = "我的共享收益（汇总 + 明细）")
    @GetMapping("/my")
    public Result<Map<String, Object>> myShareIncome(
            @Parameter(description = "业主ID") @RequestParam(required = false) Long ownerId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {

        // 业主(role=0)只能查自己的收益，忽略前端传入的 ownerId（防遍历他人收入）
        Long target = ownerId;
        if ("0".equals(currentRole) || (currentRole == null && currentUserId != null)) {
            target = parseLong(currentUserId);
        }
        if (target == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "缺少业主信息");
        }

        List<ShareIncome> records = shareIncomeMapper.selectList(
                new LambdaQueryWrapper<ShareIncome>()
                        .eq(ShareIncome::getOwnerId, target)
                        .orderByDesc(ShareIncome::getCreateTime));

        BigDecimal ownerTotal = BigDecimal.ZERO;
        BigDecimal grossTotal = BigDecimal.ZERO;
        for (ShareIncome r : records) {
            ownerTotal = ownerTotal.add(r.getOwnerAmount() == null ? BigDecimal.ZERO : r.getOwnerAmount());
            grossTotal = grossTotal.add(r.getTotalAmount() == null ? BigDecimal.ZERO : r.getTotalAmount());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", ownerTotal);          // 业主累计到手
        result.put("grossTotal", grossTotal);     // 这些订单的总停车费
        result.put("count", records.size());      // 分润笔数
        result.put("records", records);
        return Result.success(result);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
