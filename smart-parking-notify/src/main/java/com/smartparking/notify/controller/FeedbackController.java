package com.smartparking.notify.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smartparking.common.core.domain.PageResult;
import com.smartparking.common.core.domain.Result;
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
    public Result<Void> submit(@RequestBody Feedback fb) {
        fb.setStatus(0);
        fb.setCreateTime(LocalDateTime.now());
        feedbackMapper.insert(fb);
        return Result.success();
    }

    @Operation(summary = "我的反馈列表（业主）")
    @GetMapping("/my")
    public Result<PageResult<Feedback>> my(@RequestParam Long userId,
                                           @RequestParam(defaultValue = "1") Long current,
                                           @RequestParam(defaultValue = "10") Long size) {
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
        Feedback fb = feedbackMapper.selectById(id);
        if (fb == null) return Result.success(null);
        fb.setReply(body.get("reply"));
        fb.setStatus(2);
        fb.setReplyTime(LocalDateTime.now());
        feedbackMapper.updateById(fb);
        return Result.success();
    }
}
