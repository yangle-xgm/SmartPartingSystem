package com.smartparking.notify.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
import com.smartparking.common.core.exception.BizException;
import com.smartparking.common.core.exception.ErrorCode;
import com.smartparking.notify.mapper.FeedbackMapper;
import com.smartparking.notify.po.Feedback;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 业主反馈/投诉
 */
@Tag(name = "业主反馈", description = "业主提交问题，物业回复")
@RestController
@RequestMapping("/api/notify/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final FeedbackMapper feedbackMapper;

    @Operation(summary = "业主提交反馈")
    @PostMapping
    public Result<Void> submit(@RequestBody Feedback fb,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
            @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主（role=0）只能以自己的身份提交，忽略 body 里的 userId，
        // 防止替他人提交脏数据 / 伪造投诉人。
        if ("0".equals(currentRole) && currentUserId != null) {
            fb.setUserId(Long.valueOf(currentUserId));
        }
        if (fb.getUserId() == null) {
            throw new BizException(ErrorCode.DATA_INVALID, "缺少提交人");
        }
        if (fb.getContent() == null || fb.getContent().isBlank()) {
            throw new BizException(ErrorCode.DATA_INVALID, "反馈内容不能为空");
        }
        if (fb.getContent().length() > 1000) {
            throw new BizException(ErrorCode.DATA_INVALID, "反馈内容不超过 1000 字");
        }
        // 类型缺省按「咨询」；标题缺省取内容首 20 字，避免列表出现空标题
        if (fb.getType() == null) {
            fb.setType(3);
        }
        if (fb.getTitle() == null || fb.getTitle().isBlank()) {
            String plain = fb.getContent().trim();
            fb.setTitle(plain.length() > 20 ? plain.substring(0, 20) : plain);
        }
        fb.setStatus(0);
        fb.setReply(null);
        fb.setReplyTime(null);
        fb.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(fb);
        return Result.success();
    }

    @Operation(summary = "我的反馈列表（业主）")
    @GetMapping("/my")
    public Result<PageResult<Feedback>> my(@RequestParam(required = false) Long userId,
                                           @RequestParam(defaultValue = "1") Long current,
                                           @RequestParam(defaultValue = "10") Long size,
                                           @RequestHeader(value = "X-User-Id", required = false) String currentUserId,
                                           @RequestHeader(value = "X-User-Role", required = false) String currentRole) {
        // 业主只能看自己的反馈：忽略前端传入的 userId（原实现可遍历他人反馈）
        if ("0".equals(currentRole) && currentUserId != null) {
            userId = Long.valueOf(currentUserId);
        }
        if (userId == null) {
            throw new BizException(ErrorCode.DATA_INVALID, "缺少用户ID");
        }
        Page<Feedback> page = new Page<>(current, size);
        var result = feedbackMapper.selectPage(page,
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Feedback>()
                        .eq(Feedback::getUserId, userId)
                        .orderByDesc(Feedback::getCreateTime));
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(), current, size));
    }

    @Operation(summary = "全部反馈（管理员）")
    @GetMapping("/all")
    public Result<PageResult<Feedback>> all(@RequestParam(defaultValue = "1") Long current,
                                            @RequestParam(defaultValue = "10") Long size,
                                            @RequestParam(required = false) Integer status) {
        Page<Feedback> page = new Page<>(current, size);
        var wrapper = new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Feedback>()
                .eq(status != null, Feedback::getStatus, status)
                .orderByDesc(Feedback::getCreateTime);
        var result = feedbackMapper.selectPage(page, wrapper);
        return Result.success(new PageResult<>(result.getTotal(), result.getRecords(), current, size));
    }

    @Operation(summary = "管理员回复反馈")
    @PostMapping("/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String reply = body == null ? null : body.get("reply");
        if (reply == null || reply.isBlank()) {
            throw new BizException(ErrorCode.DATA_INVALID, "回复内容不能为空");
        }
        Feedback fb = feedbackMapper.selectById(id);
        if (fb == null) {
            throw new BizException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        fb.setReply(reply);
        fb.setStatus(2);
        fb.setReplyTime(LocalDateTime.now());
        // 0 行受影响说明记录已被并发删除，不能当成功返回
        if (feedbackMapper.updateById(fb) <= 0) {
            throw new BizException(ErrorCode.FEEDBACK_NOT_FOUND);
        }
        return Result.success();
    }
}
