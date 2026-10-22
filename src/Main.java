import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main {

    /* ==================== Goods ==================== */
    static class Goods {
        private static final Logger LOG = Logger.getLogger(Goods.class.getName());
        private static final String DEFAULT_NAME = "未知商品";
        private static final BigDecimal DEFAULT_PRICE = BigDecimal.ZERO;

        private final String name;
        private final BigDecimal price;

        public Goods(String name, BigDecimal price) {
            String validName = DEFAULT_NAME;
            BigDecimal validPrice = DEFAULT_PRICE;

            try {
                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException("商品名稱不能為空");
                }
                validName = name.trim();
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "{0}，已使用默認名稱：{1}", new Object[]{e.getMessage(), DEFAULT_NAME});
            }

            try {
                if (price == null || price.compareTo(BigDecimal.ZERO) < 0) {
                    throw new IllegalArgumentException("商品價格非法");
                }
                validPrice = price;
            } catch (IllegalArgumentException e) {
                LOG.log(Level.WARNING, "{0}（name={1}, price={2}），已使用默認價格 0",
                        new Object[]{e.getMessage(), validName, price});
            }

            this.name = validName;
            this.price = validPrice;
        }

        public String getName() { return name; }
        public BigDecimal getPrice() { return price; }
    }

    /* ==================== CartItem ==================== */
    static class CartItem {
        private static final Logger LOG = Logger.getLogger(CartItem.class.getName());

        private final Goods goods;
        private final int quantity;

        public CartItem(Goods goods, double quantity) {
            Goods validGoods = goods;
            int validQuantity = 0;

            try {
                if (goods == null) {
                    throw new IllegalArgumentException("商品不能為空");
                }
            } catch (IllegalArgumentException e) {
                LOG.warning("購物車條目異常：" + e.getMessage() + "，已替換為「空商品」並將斤數設為 0");
                this.goods = new Goods("空商品", BigDecimal.ZERO);
                this.quantity = 0;
                return;
            }

            try {
                if (quantity % 1 != 0) {
                    throw new IllegalArgumentException(
                            String.format("%s的斤數 %s 包含小數，系統只支持整斤購買", validGoods.getName(), quantity));
                }
            } catch (IllegalArgumentException e) {
                LOG.warning("購物車條目異常：" + e.getMessage() + "，已向下取整");
            }
            validQuantity = (int) quantity;

            try {
                if (validQuantity < 0) {
                    throw new IllegalArgumentException(
                            String.format("%s的購買斤數不能為負數（輸入值：%s）", validGoods.getName(), validQuantity));
                }
            } catch (IllegalArgumentException e) {
                LOG.warning("購物車條目異常：" + e.getMessage() + "，已強制設為 0 斤");
                validQuantity = 0;
            }

            this.goods = validGoods;
            this.quantity = validQuantity;
        }

        public Goods getGoods() { return goods; }
        public int getQuantity() { return quantity; }

        public BigDecimal subtotal() {
            return goods.getPrice().multiply(BigDecimal.valueOf(quantity));
        }
    }

    /* ==================== Discount 接口與實現 ==================== */
    interface Discount {
        BigDecimal apply(Cart cart, BigDecimal amount);
    }

    static class StrawberryDiscount implements Discount {
        private static final String STRAWBERRY = "草莓";
        private static final BigDecimal PAY_RATE = new BigDecimal("0.8");

        @Override
        public BigDecimal apply(Cart cart, BigDecimal amount) {
            BigDecimal strawberrySubtotal = cart.getItems().stream()
                    .filter(item -> STRAWBERRY.equals(item.getGoods().getName()))
                    .map(CartItem::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal discountAmount = strawberrySubtotal.multiply(BigDecimal.ONE.subtract(PAY_RATE));
            return amount.subtract(discountAmount);
        }
    }

    static class FullReductionDiscount implements Discount {
        private final BigDecimal threshold;
        private final BigDecimal reduction;

        public FullReductionDiscount(BigDecimal threshold, BigDecimal reduction) {
            this.threshold = threshold;
            this.reduction = reduction;
        }

        @Override
        public BigDecimal apply(Cart cart, BigDecimal amount) {
            if (amount.compareTo(threshold) >= 0) {
                return amount.subtract(reduction);
            }
            return amount;
        }
    }

    /* ==================== Cart 購物車 ==================== */
    static class Cart {
        private final List<CartItem> items = new ArrayList<>();

        public Cart add(Goods goods, double quantity) {
            CartItem item = new CartItem(goods, quantity);
            if (item.getQuantity() > 0) {
                items.add(item);
            }
            return this;
        }

        public List<CartItem> getItems() { return Collections.unmodifiableList(items); }

        public BigDecimal subtotal() {
            return items.stream().map(CartItem::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public BigDecimal checkout(List<Discount> discounts) {
            BigDecimal amount = subtotal();
            if (discounts != null) {
                for (Discount discount : discounts) {
                    amount = discount.apply(this, amount);
                }
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
    }

    /* ==================== SupermarketService ==================== */
    static class SupermarketService {
        public static final Goods APPLE = new Goods("蘋果", new BigDecimal("8"));
        public static final Goods STRAWBERRY = new Goods("草莓", new BigDecimal("13"));
        public static final Goods MANGO = new Goods("芒果", new BigDecimal("20"));

        public static final Discount STRAWBERRY_DISCOUNT = new StrawberryDiscount();
        public static final Discount FULL_100_MINUS_10 = new FullReductionDiscount(new BigDecimal("100"), new BigDecimal("10"));

        public BigDecimal calculate(Cart cart, List<Discount> discounts) {
            return cart.checkout(discounts);
        }
    }

    /* ==================== 測試入口 ==================== */
    public static void main(String[] args) {
        System.setErr(System.out);
        configureLogging();
        SupermarketService service = new SupermarketService();

        // 題目 1：蘋果 10 斤，草莓 20 斤 = 340
        Cart cart1 = new Cart().add(SupermarketService.APPLE, 10).add(SupermarketService.STRAWBERRY, 20);
        check("題目1 顧客A 蘋果10斤 草莓20斤", service.calculate(cart1, null), "340");

        // 題目 2：蘋果 15 斤，草莓 25 斤，芒果 10 斤 = 645
        Cart cart2 = new Cart().add(SupermarketService.APPLE, 15).add(SupermarketService.STRAWBERRY, 25).add(SupermarketService.MANGO, 10);
        check("題目2 顧客B 蘋果15斤 草莓25斤 芒果10斤", service.calculate(cart2, null), "645");

        // 題目 3：蘋果 20 斤，草莓 30 斤，芒果 15 斤 = 772
        Cart cart3 = new Cart().add(SupermarketService.APPLE, 20).add(SupermarketService.STRAWBERRY, 30).add(SupermarketService.MANGO, 15);
        check("題目3 顧客C 蘋果20斤 草莓30斤 芒果15斤", service.calculate(cart3, Collections.singletonList(SupermarketService.STRAWBERRY_DISCOUNT)), "772");

        // 題目 4：蘋果 201 斤，草莓 340 斤，芒果 151 斤 = 8154
        Cart cart4 = new Cart().add(SupermarketService.APPLE, 201).add(SupermarketService.STRAWBERRY, 340).add(SupermarketService.MANGO, 151);
        check("題目4 顧客D 蘋果201斤 草莓340斤 芒果151斤", service.calculate(cart4, Arrays.asList(SupermarketService.STRAWBERRY_DISCOUNT, SupermarketService.FULL_100_MINUS_10)), "8154");

        // 題目 4：蘋果 20 斤，草莓 30 斤，芒果 15 斤 = 762
        check("題目4 顧客C 蘋果20斤 草莓30斤 芒果15斤", service.calculate(cart3, Arrays.asList(SupermarketService.STRAWBERRY_DISCOUNT, SupermarketService.FULL_100_MINUS_10)), "762");
    }

    /* ==================== 日誌格式化與斷言 ==================== */
    private static void configureLogging() {
        Logger root = Logger.getLogger("");
        root.setLevel(Level.INFO);
        for (Handler handler : root.getHandlers()) {
            handler.setFormatter(new SimpleFormatter() {
                @Override
                public String format(LogRecord record) {
                    return String.format("[%s] %s%n", record.getLevel(), formatMessage(record));
                }
            });
        }
    }

    private static final Logger LOG = Logger.getLogger(Main.class.getName());

    private static void check(String caseName, BigDecimal actual, String expected) {
        try {
            BigDecimal expectedValue = new BigDecimal(expected);
            if (actual.compareTo(expectedValue) != 0) {
                LOG.log(Level.WARNING, "【測試未通過】用例：{0}。期望值：{1}，實際值：{2}",
                        new Object[]{caseName, expectedValue, actual});
                System.out.println("[FAIL] " + caseName + "\t\t期望: " + expectedValue + "\t實際: " + actual);
            } else {
                System.out.println("[PASS] " + caseName + "\t\t" + actual);
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "【測試異常】用例：{0}。異常信息：{1}", new Object[]{caseName, e.getMessage()});
            System.out.println("[ERROR] " + caseName + "\t\t[執行異常] " + e.getMessage());
        }
    }
}