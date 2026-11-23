package cn.mcfhm.furniture;

public final class Text {

    private Text() {
    }

    /** 把 & 颜色代码转换成 § 代码 */
    public static String colorize(String input) {
        if (input == null) {
            return "";
        }
        return input.replace('&', '\u00A7');
    }
}
