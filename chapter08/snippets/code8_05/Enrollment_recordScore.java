public void recordScore(Score newScore) {
    if (deleted || status != EnrollmentStatus.ACTIVE) {
        throw new DomainException("无效选课记录不能录入成绩");
    }
    this.score = Objects.requireNonNull(newScore);
}
