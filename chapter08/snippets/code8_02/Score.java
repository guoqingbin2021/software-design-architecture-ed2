@Embeddable
public class Score {
    @Column(name = "score_value", precision = 5, scale = 2)
    private BigDecimal value;

    protected Score() {} // 供JPA使用

    public Score(BigDecimal value) {
        if (value == null
                || value.compareTo(BigDecimal.ZERO) < 0
                || value.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("成绩必须在0到100之间");
        }
        this.value = value;
    }

    public BigDecimal getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof Score s && value.compareTo(s.value) == 0;
    }

    @Override
    public int hashCode() {
        return value.stripTrailingZeros().hashCode();
    }
}
