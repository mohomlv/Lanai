package com.landata.shared.security;

import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class RoleGuard {
  private final AccessGuard accessGuard;

  public RoleGuard(AccessGuard accessGuard) {
    this.accessGuard = accessGuard;
  }

  @Before("@annotation(role)")
  public void check(RequiresRole role) {
    accessGuard.require(role.value());
  }
}
