/** 程序启动入口：在 IDEA 中运行此类即可进入购物系统。 */
public class Main {
    public static void main(String[] args) {
        // 将启动请求交给 shop 包中的菜单类，业务代码按职责分开管理。
        shop.Main.main(args);
    }
}
