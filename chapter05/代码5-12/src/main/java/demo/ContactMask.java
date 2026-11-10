package demo;

final class ContactMask {
    private ContactMask() {}

    static String phone(String value) {
        if (value == null || value.isEmpty()) return value;
        if (!value.matches("[0-9]{8,}")) return "***";
        return value.substring(0, 3)
                + "*".repeat(value.length() - 7)
                + value.substring(value.length() - 4);
    }

    static String email(String value) {
        if (value == null || value.isEmpty()) return value;
        int at = value.indexOf('@');
        if (at <= 0 || at != value.lastIndexOf('@') || at == value.length() - 1) {
            return "***";
        }
        int firstCharacterEnd = value.offsetByCodePoints(0, 1);
        return value.substring(0, firstCharacterEnd) + "***" + value.substring(at);
    }
}
