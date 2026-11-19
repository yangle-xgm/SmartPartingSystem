package com.smartparking.payment.controller;

import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.payment.dto.InvoiceApplyDTO;
import com.smartparking.payment.po.Invoice;
import com.smartparking.payment.service.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 电子发票控制器。
 *
 * <p>业主端：{@code /apply} 申请、{@code /my} 查自己的发票；
 * 管理端：统一挂在 {@code /admin/**} 下，由网关 {@code ADMIN_ONLY_PATTERNS} 整段限制为管理员。</p>
 */
@Tag(name = "电子发票", description = "停车费电子发票申请与开具")
@RestController
@RequestMapping("/api/payment/invoice")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceService invoiceService;

    @Operation(summary = "申请开票（业主，仅限本人已支付订单）")
    @PostMapping("/apply")
    public Result<Invoice> apply(@RequestBody @Valid InvoiceApplyDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        return Result.success(invoiceService.apply(dto, parseLong(currentUserId)));
    }

    @Operation(summary = "我的发票")
    @GetMapping("/my")
    public Result<List<Invoice>> my(@RequestHeader(value = "X-User-Id", required = false) String currentUserId) {
        return Result.success(invoiceService.myInvoices(parseLong(currentUserId)));
    }

    @Operation(summary = "管理端：发票申请列表")
    @GetMapping("/admin/list")
    public Result<List<Invoice>> adminList(
            @Parameter(description = "状态（0待开票 1已开票 2已红冲，空为全部）")
            @RequestParam(required = false) Integer status,
            @Parameter(description = "关键字（抬头 / 发票号 / 税号）")
            @RequestParam(required = false) String keyword,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        requireAdmin(currentRole);
        return Result.success(invoiceService.adminList(status, keyword));
    }

    @Operation(summary = "管理端：开具发票")
    @PostMapping("/admin/{id}/issue")
    public Result<Invoice> issue(@PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        requireAdmin(currentRole);
        return Result.success(invoiceService.issue(id, parseLong(currentUserId)));
    }

    @Operation(summary = "管理端：红冲发票")
    @PostMapping("/admin/{id}/red-flush")
    public Result<Invoice> redFlush(@PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        requireAdmin(currentRole);
        String reason = body == null ? null : body.get("reason");
        return Result.success(invoiceService.redFlush(id, reason, parseLong(currentUserId)));
    }

    private void requireAdmin(String role) {
        if (!"1".equals(role) && !"2".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "发票开具仅管理员可操作");
        }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
