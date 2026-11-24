-- ========================================
-- 07. 文件中心：文件元数据（DDL）
--   覆盖：sys_file
--   前置：01_platform_tenant_app.sql（tenant_code 语义）
--   说明：使用文件上传/查询/下载能力前必须建表，否则 FileService 查询元数据会失败
-- ========================================

CREATE TABLE sys_file (
  id            VARCHAR(32)  PRIMARY KEY,
  tenant_code   VARCHAR(20)  NOT NULL COMMENT '租户编码',
  file_name     VARCHAR(255) NOT NULL COMMENT '原始文件名，仅用于展示',
  storage_type  VARCHAR(16)  NOT NULL DEFAULT 'local' COMMENT '存储引擎：local/s3',
  storage_key   VARCHAR(512) NOT NULL COMMENT '服务端生成的相对路径',
  file_size     BIGINT       NOT NULL COMMENT '字节数',
  content_type  VARCHAR(128) COMMENT '服务端判定的安全 MIME',
  file_ext      VARCHAR(32)  COMMENT '小写扩展名',
  sha256        VARCHAR(64)  COMMENT '文件 SHA-256 摘要',
  biz_type      VARCHAR(64)  COMMENT '业务类型，如 factoryVisitApply',
  biz_id        VARCHAR(64)  COMMENT '业务单据 ID',
  remark        VARCHAR(255) COMMENT '备注',
  creator_id    VARCHAR(10) COMMENT '创建人id',
  creator_name  VARCHAR(50) COMMENT '创建人名称',
  modifier_id   VARCHAR(10) COMMENT '修改人Id',
  modifier_name VARCHAR(50) COMMENT '修改人名称',
  create_time   DATETIME COMMENT '创建时间',
  modified_time DATETIME COMMENT '修改日期',
  is_deleted    BIT DEFAULT b'0' COMMENT '删除标记',
  INDEX idx_file_tenant_time (tenant_code, create_time),
  INDEX idx_file_biz (biz_type, biz_id),
  INDEX idx_file_sha256 (sha256)
) COMMENT='文件元数据';
