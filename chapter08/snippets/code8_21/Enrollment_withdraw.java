public void withdraw(Instant now, Instant deadline) {
    if (deleted || status != EnrollmentStatus.ACTIVE) {
        throw new DomainException("记录不允许退课");
    }
    if (now.isAfter(deadline) || score != null) {
        throw new DomainException("超过期限或已有成绩");
    }
    status = EnrollmentStatus.WITHDRAWN;
    withdrawnAt = now;
}
