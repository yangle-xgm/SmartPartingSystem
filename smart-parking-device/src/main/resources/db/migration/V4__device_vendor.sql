-- ============================================================
-- 设备厂商渠道标识
--
-- 目的：把「设备接的是哪家厂商的协议」变成数据而不是硬编码。
-- 服务端按 vendor 选择对应的 GateAdapter；不填则用配置项
-- smart-parking.device.gate-vendor 指定的默认通道（默认 sandbox）。
--
-- 取值约定：
--   sandbox   —— 内置沙箱道闸（JSON 指令 + ACK 回执，可被内置模拟器应答）
--   mqtt-plain—— 通用 MQTT 纯文本指令（OPEN/CLOSE），兼容已有第三方 broker 模拟器
--   其它厂商（如 zkteco / hikvision）—— 按需实现 GateAdapter 后填厂商码即可接入，
--                                     业务代码零改动。
-- ============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.columns
           WHERE table_schema = DATABASE() AND table_name = 'device' AND column_name = 'vendor');
SET @sql := IF(@c = 0,
  'ALTER TABLE `device` ADD COLUMN `vendor` VARCHAR(32) NOT NULL DEFAULT ''sandbox'' COMMENT ''接入厂商通道（sandbox 沙箱 / mqtt-plain 通用MQTT / 厂商码）'' AFTER `mqtt_topic`',
  'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
