import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class Main {

    /* ==================== 商品 ==================== */
    static final class Goods {
        private final String name;
        private final BigDecimal price;

        Goods(String name, BigDecimal price) {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("商品名称不能为空");
            }
            if (price == null || price.signum() < 0) {
                throw new IllegalArgumentException("商品价格非法: " + price);
            }
            this.name = name.trim();
            this.price = price;
        }

        String name() { return name; }
        BigDecimal price() { return price; }

        @Override public String toString() {
            return name + "@" + price;
        }
    }

    /* ==================== 购物车 ==================== */
    static final class CartItem {
        private final Goods goods;
        private final int quantity;

        CartItem(Goods goods, int quantity) {
            if (goods == null) {
                throw new IllegalArgumentException("商品不能为空");
            }
            if (quantity < 0) {
                throw new IllegalArgumentException(
                        goods.name() + " 的购买斤数必须为非负整数，输入：" + quantity);
            }
            this.goods = goods;
            this.quantity = quantity;
        }

        Goods goods() { return goods; }
        int quantity() { return quantity; }

        BigDecimal subtotal() {
            return goods.price().multiply(BigDecimal.valueOf(quantity));
        }
    }

    /* ==================== 折扣策略 ==================== */
    interface Discount {
        BigDecimal apply(Cart cart, BigDecimal amount);
    }

    /** 对指定商品（按引用匹配）按折扣率支付 */
    static final class PercentOff implements Discount {
        private final Goods target;
        private final BigDecimal payRate;

        PercentOff(Goods target, BigDecimal payRate) {
            if (target == null) {
                throw new IllegalArgumentException("折扣目标商品不能为空");
            }
            if (payRate == null
                    || payRate.signum() < 0
                    || payRate.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("折扣率必须在 [0,1] 之间：" + payRate);
            }
            this.target = target;
            this.payRate = payRate;
        }

        @Override
        public BigDecimal apply(Cart cart, BigDecimal amount) {
            BigDecimal targetSubtotal = cart.items().stream()
                    .filter(i -> i.goods() == target)   // 引用比较，避免字符串耦合
                    .map(CartItem::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal saved = targetSubtotal
                    .multiply(BigDecimal.ONE.subtract(payRate));
            return amount.subtract(saved);
        }
    }

    /** 满 threshold 减 reduction */
    static final class FullReduction implements Discount {
        private final BigDecimal threshold;
        private final BigDecimal reduction;

        FullReduction(BigDecimal threshold, BigDecimal reduction) {
            if (threshold == null || threshold.signum() <= 0) {
                throw new IllegalArgumentException("满减门槛必须为正数：" + threshold);
            }
            if (reduction == null
                    || reduction.signum() < 0
                    || reduction.compareTo(threshold) > 0) {
                throw new IllegalArgumentException("减免额非法：" + reduction);
            }
            this.threshold = threshold;
            this.reduction = reduction;
        }

        @Override
        public BigDecimal apply(Cart cart, BigDecimal amount) {
            return amount.compareTo(threshold) >= 0
                    ? amount.subtract(reduction)
                    : amount;
        }
    }

    /* ==================== 购物车 ==================== */
    static final class Cart {
        private final List<CartItem> items = new ArrayList<>();

        Cart add(Goods goods, int quantity) {
            items.add(new CartItem(goods, quantity));
            return this;
        }

        List<CartItem> items() {
            return List.copyOf(items);
        }

        BigDecimal subtotal() {
            return items.stream()
                    .map(CartItem::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal checkout(List<Discount> discounts) {
            BigDecimal amount = subtotal();
            if (discounts != null) {
                for (Discount d : discounts) {
                    amount = d.apply(this, amount);
                }
            }
            if (amount.signum() < 0) {
                amount = BigDecimal.ZERO;
            }
            return amount.setScale(2, RoundingMode.HALF_UP);
        }
    }

    /* ==================== 超市配置（实例化，可替换） ==================== */
    static final class Supermarket {
        final Goods apple      = new Goods("苹果", new BigDecimal("8"));
        final Goods strawberry = new Goods("草莓", new BigDecimal("13"));
        final Goods mango      = new Goods("芒果", new BigDecimal("20"));

        final Discount strawberry20Off = new PercentOff(strawberry, new BigDecimal("0.8"));
        final Discount full100Minus10  = new FullReduction(
                new BigDecimal("100"), new BigDecimal("10"));
    }

    /* ==================== 测试入口 ==================== */
    public static void main(String[] args) {
        Supermarket shop = new Supermarket();

        check("题目1 顾客A 苹果10斤 草莓20斤",
                340,
                new Cart().add(shop.apple, 10).add(shop.strawberry, 20),
                List.of());

        check("题目2 顾客B 苹果15斤 草莓25斤 芒果10斤",
                645,
                new Cart().add(shop.apple, 15).add(shop.strawberry, 25).add(shop.mango, 10),
                List.of());

        check("题目3 顾客C 苹果20斤 草莓30斤 芒果15斤（草莓8折）",
                772,
                new Cart().add(shop.apple, 20).add(shop.strawberry, 30).add(shop.mango, 15),
                List.of(shop.strawberry20Off));

        check("题目4 顾客D 苹果201斤 草莓340斤 芒果151斤（草莓8折+满100减10）",
                8154,
                new Cart().add(shop.apple, 201).add(shop.strawberry, 340).add(shop.mango, 151),
                List.of(shop.strawberry20Off, shop.full100Minus10));

        check("题目4 顾客C 苹果20斤 草莓30斤 芒果15斤（草莓8折+满100减10）",
                762,
                new Cart().add(shop.apple, 20).add(shop.strawberry, 30).add(shop.mango, 15),
                List.of(shop.strawberry20Off, shop.full100Minus10));

    }

    private static void check(String name, long expected, Cart cart, List<Discount> discounts) {
        BigDecimal actual = cart.checkout(discounts);
        BigDecimal exp = BigDecimal.valueOf(expected);
        if (actual.compareTo(exp) == 0) {
            System.out.printf("[PASS] %s  -> %s%n", name, actual);
        } else {
            System.out.printf("[FAIL] %s  期望: %s  实际: %s%n", name, exp, actual);
        }
    }

    private static void expectFailure(String name, Runnable action) {
        try {
            action.run();
            System.out.printf("[FAIL] %s  期望抛异常但未抛出%n", name);
        } catch (IllegalArgumentException e) {
            System.out.printf("[PASS] %s  -> %s%n", name, e.getMessage());
        }
    }
}