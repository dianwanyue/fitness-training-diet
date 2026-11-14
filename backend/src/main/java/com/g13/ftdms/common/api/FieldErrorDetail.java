package com.g13.ftdms.common.api;

/**
 * 字段级校验错误明细（接口文档 2.6 的 {@code data.errors[]}）。
 *
 * @param field   出错字段的 JSON 路径，如 {@code items[0].targetSets}
 * @param message 面向用户的中文提示
 */
public record FieldErrorDetail(String field, String message) {
}
