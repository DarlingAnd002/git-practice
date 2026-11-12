package shop;

/** 页面帮助集中放在这里，按钮按当前页签取对应说明。 */
final class HelpContent {
    record Content(String title, String body) { }

    static Content page(boolean login, boolean seller, int tab) {
        if (login) return tab == 0 ? new Content("登录（Log in）", """
                输入账号和密码，点击登录。系统根据账号自动识别消费者或销售者。
                (Enter your username and password. Your account determines your role.)

                没有账号时切换到注册页。密码输入显示星号；输错密码后需要重新输入。
                (Open Register to create an account. Password input is masked and cleared after an attempt.)
                """) : new Content("注册（Register）", """
                账号使用 3–24 位字母、数字或下划线；密码为 8–128 位，两次输入必须相同。
                (Use 3–24 letters, digits or underscores for the username and 8–128 characters for the password. Confirm the same password.)

                选择消费者或销售者。消费者浏览和购买商品；销售者管理自己的商品和销售订单。注册后返回登录。
                (Choose Customer to shop, or Seller to manage your products and sales orders. Log in after registering.)
                """);
        if (tab == 0 && seller) return new Content("我的商品（My products）", """
                添加商品时填写名称、价格和库存，编号自动分配，随后可选择商品图片。
                (Add a name, price and stock. A product ID is assigned automatically, then you can upload an image.)

                单击条目或左侧复选框选中商品；双击条目编辑名称、价格、库存和图片。勾选多个商品可批量上架或下架。
                (Click a row or checkbox to select. Double-click to edit. Select multiple products to list or unlist them.)

                只能管理自己的商品。下架后保留编号、库存和图片，可在编辑窗口重新上架。库存为 0 时显示售罄。
                (Manage only your products. Unlisting preserves their data; relist them from the editor. Zero stock means sold out.)

                搜索支持名称、编号和销售者，可按状态筛选。图片点击查看大图：滚轮缩放、左键拖动、双击切换大小；点击别处、Esc 或右键关闭。
                (Search by name, ID or seller and filter by status. Click an image to view; scroll to zoom, drag to pan, double-click to change size. Click elsewhere, press Esc or right-click to close.)
                """);
        if (tab == 0) return new Content("商品（Products）", """
                搜索名称、编号或销售者，按有货或售罄筛选。单击条目或复选框选择商品，支持直接鼠标多选。
                (Search by name, ID or seller. Filter by stock status and click rows or checkboxes to select multiple products.)

                双击商品查看详情，可加入购物车或立即购买。勾选多个商品可批量加购。数量留空默认为 1，不能超过库存或单项数量上限 99。
                (Double-click for details, Add to cart or Buy now. Add selected products in a batch. Blank quantity defaults to 1; stock and the per-item limit of 99 apply.)

                点击图片打开无边框独立预览，不会改变勾选。滚轮缩放、拖动查看，双击切换大小；点击别处、Esc 或右键关闭。
                (Click an image to preview without changing selection. Scroll to zoom, drag to pan, double-click to change size. Click elsewhere, press Esc or right-click to close.)
                """);
        if (!seller && tab == 1) return new Content("购物车（Cart）", """
                购物车按商家分组。勾选商品、商家或底部全选框决定本次结账的商品，小计和总额只统计已选项。
                (Items are grouped by seller. Select individual items, a seller group or all items. Totals include selected items only.)

                点击数量框会清空以方便输入，留空保留当前数量；回车或移开焦点生效，也可用加减按钮调整。数量错误时先修改才能结账。
                (Click the quantity field to replace it. Blank keeps its current value. Press Enter or leave the field to apply, or use plus/minus. Fix invalid quantities before checkout.)

                右侧删除移除单件，底部删除移除勾选商品。售完或下架商品保留显示并禁止购买，结账时可确认删除它们后继续；库存不足时先调整数量。
                (Remove one item or remove selected items. Sold-out or unlisted items remain visible and cannot be purchased. Checkout can remove them after confirmation. Reduce quantities when stock is insufficient.)

                结账确认窗口可勾选“我是学生，享九折”，默认不勾选。本次购买按学生自行声明享九折，每个条目四舍五入到分后汇总。
                (Select Student: 10% off at checkout to declare student eligibility for this purchase. Each item total is rounded to cents before summing.)

                结账前确认各商家小计和总金额；不同商家生成独立订单。同一商家的商品合并为一单，购买记录中可查看。
                (Confirm seller subtotals and the total. Checkout creates one order per seller, available in Purchase history.)
                """);
        return new Content(seller ? "销售订单（Sales orders）" : "购买记录（Purchase history）", """
                列表显示订单时间、买家、商家、商品摘要、件数和金额。销售者只看自己的销售记录，消费者只看自己的购买记录。
                (View dates, buyers, sellers, products, quantities and amounts. Each account sees only its own orders.)

                点击表头排序；双击订单查看完整编号、商品明细和成交价格。列表中缩短的编号可悬停查看。
                (Click a header to sort. Double-click for the full order ID, item details and prices. Hover over a shortened ID to read it.)

                历史订单保留购买时的名称和价格，后续修改商品不影响记录。新结账按商家拆单，旧历史订单保留原样。
                (Historical names and prices stay unchanged. New purchases create separate orders per seller; older records are preserved.)
                """);
    }

    static Content dialog(String title) {
        if (title.contains("订单详情")) return new Content(title, "成交明细保留购买时的商品、商家、单价、数量和实付金额。\n(Order details preserve the product, seller, unit price, quantity and amount at purchase.)");
        if (title.contains("商品图片")) return new Content(title, "选择、更换或删除商品主图，支持 PNG/JPG，最大 10 MB、1200 万像素。图片单独立即保存，关闭窗口不会撤销。点击缩略图查看原图。\n(Choose, replace or remove the main image. PNG/JPG up to 10 MB and 12 MP are supported. Image changes save immediately; closing does not undo them. Click the thumbnail to preview.)");
        if (title.contains("编辑商品")) return new Content(title, "修改名称、价格和库存后保存；价格必须大于 0，库存可为 0。下架保留商品，重新上架时保存当前编辑内容。图片操作单独立即保存；取消只放弃其他未保存字段。\n(Edit the name, positive price and non-negative stock, then save. Unlist preserves the product; relisting saves current edits. Image changes save immediately. Cancel discards only other unsaved fields.)");
        if (title.contains("添加商品")) return new Content(title, "填写名称、正数价格和初始库存，编号自动生成，创建后可上传图片。取消不创建商品。\n(Enter a name, positive price and initial stock. The ID is automatic; add an image after creation. Cancel creates nothing.)");
        if (title.contains("批量加入")) return new Content(title, "填写每种商品要加入的数量，留空为 1。所有勾选商品均通过库存检查后才一起加入；任一商品失败时不部分加入。\n(Enter the quantity per selected product; blank means 1. All items must pass stock checks before anything is added.)");
        if (title.contains("购物车商品")) return new Content(title, "修改数量后保存；0 表示删除。购买该商品只结算本条目；其余购物车商品保留。数量不可超过库存或 99。\n(Save the new quantity; 0 removes the item. Buy this item checks out only this row. Other items stay in the cart. Stock and the limit of 99 apply.)");
        return new Content(title, "填写购买数量，留空为 1。加入购物车会保留商品供以后结账；立即购买只购买本商品，不影响原购物车。点击图片可查看清晰大图。\n(Enter a quantity; blank means 1. Add to cart for later or Buy now for this product only. Your existing cart is preserved. Click the image for a full-size preview.)");
    }
}
