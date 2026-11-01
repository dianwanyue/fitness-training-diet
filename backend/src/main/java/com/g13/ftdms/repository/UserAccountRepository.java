package com.g13.ftdms.repository;

import com.g13.ftdms.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户账户仓储。
 *
 * <p>只声明"必须走数据库"的查询；简单的按主键读写由 JpaRepository 提供。
 * 方法名即查询语义（Spring Data 派生查询），无需手写 SQL，降低出错面。</p>
 */
public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    /** 按用户名查询（登录、注册查重） */
    Optional<UserAccount> findByUsername(String username);

    /** 判断用户名是否已存在（注册前置校验，避免依赖唯一索引异常） */
    boolean existsByUsername(String username);

    /** 查询第一个指定角色的账户（用于判断系统里是否已有管理员） */
    Optional<UserAccount> findFirstByRole(String role);
}
