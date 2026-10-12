package com.smartparking.user.controller;

import com.smartparking.common.core.domain.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import lombok.RequiredArgsConstructor;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user/community")
@RequiredArgsConstructor
public class CommunityController {

    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list() {
        return Result.success(jdbcTemplate.queryForList("SELECT * FROM community ORDER BY id"));
    }
}
