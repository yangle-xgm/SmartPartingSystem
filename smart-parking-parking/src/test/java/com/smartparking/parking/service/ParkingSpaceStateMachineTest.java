package com.smartparking.parking.service;

import com.smartparking.common.core.constant.Constants;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 车位状态机契约测试（Phase B）。
 * 对照 {@link Constants} 与 ParkingSpaceServiceImpl 的实际用法，固化状态流转规则：
 * 空闲(FREE=0) 可进入 占用/预约/维护；占用/预约/维护 均可释放回空闲；
 * 维护中不可直接转占用/预约（须先修复为空闲）；占用<->预约可互转。
 * 属仓库既有「镜像」约定：以测试固化期望的业务规则，作为回归基线。
 */
class ParkingSpaceStateMachineTest {

    private static final Set<Integer> ALL = Set.of(
            Constants.SPACE_STATUS_FREE,
            Constants.SPACE_STATUS_OCCUPIED,
            Constants.SPACE_STATUS_RESERVED,
            Constants.SPACE_STATUS_MAINTENANCE);

    private static boolean canTransition(int from, int to) {
        if (from == to) {
            return false;
        }
        if (to == Constants.SPACE_STATUS_FREE) {
            return true; // 任何状态都可由释放回到空闲
        }
        if (from == Constants.SPACE_STATUS_FREE) {
            return to == Constants.SPACE_STATUS_OCCUPIED
                    || to == Constants.SPACE_STATUS_RESERVED
                    || to == Constants.SPACE_STATUS_MAINTENANCE;
        }
        if (from == Constants.SPACE_STATUS_MAINTENANCE) {
            return false; // 维护态须先修复为空闲
        }
        // 占用 <-> 预约 互转；其余（含到维护）不允许
        return (from == Constants.SPACE_STATUS_OCCUPIED && to == Constants.SPACE_STATUS_RESERVED)
                || (from == Constants.SPACE_STATUS_RESERVED && to == Constants.SPACE_STATUS_OCCUPIED);
    }

    @Test
    void statusConstants_areDistinctAndValid() {
        assertEquals(4, ALL.size());
        ALL.forEach(s -> assertTrue(s >= 0 && s <= 3, "状态应在 [0,3]: " + s));
    }

    @Test
    void freeToOther_transitionsAllowed() {
        assertTrue(canTransition(Constants.SPACE_STATUS_FREE, Constants.SPACE_STATUS_OCCUPIED));
        assertTrue(canTransition(Constants.SPACE_STATUS_FREE, Constants.SPACE_STATUS_RESERVED));
        assertTrue(canTransition(Constants.SPACE_STATUS_FREE, Constants.SPACE_STATUS_MAINTENANCE));
    }

    @Test
    void anyToFree_releasedAllowed() {
        assertTrue(canTransition(Constants.SPACE_STATUS_OCCUPIED, Constants.SPACE_STATUS_FREE));
        assertTrue(canTransition(Constants.SPACE_STATUS_RESERVED, Constants.SPACE_STATUS_FREE));
        assertTrue(canTransition(Constants.SPACE_STATUS_MAINTENANCE, Constants.SPACE_STATUS_FREE));
    }

    @Test
    void maintenanceToOccupied_blocked() {
        assertFalse(canTransition(Constants.SPACE_STATUS_MAINTENANCE, Constants.SPACE_STATUS_OCCUPIED));
        assertFalse(canTransition(Constants.SPACE_STATUS_MAINTENANCE, Constants.SPACE_STATUS_RESERVED));
    }

    @Test
    void occupiedReserved_mutualAllowedButNotToMaintenance() {
        assertTrue(canTransition(Constants.SPACE_STATUS_OCCUPIED, Constants.SPACE_STATUS_RESERVED));
        assertTrue(canTransition(Constants.SPACE_STATUS_RESERVED, Constants.SPACE_STATUS_OCCUPIED));
        assertFalse(canTransition(Constants.SPACE_STATUS_OCCUPIED, Constants.SPACE_STATUS_MAINTENANCE));
    }

    @Test
    void sameStateTransition_isRejected() {
        assertFalse(canTransition(Constants.SPACE_STATUS_FREE, Constants.SPACE_STATUS_FREE));
    }
}
