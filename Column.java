// Java 实现
public class Column {
    private Object amount;
    private String name;
    
    public Column(Object amount, String name) {
        this.amount = amount;
        this.name = name;
    }
    
    public Object getAmount() {
        return amount;
    }
    
    public String getName() {
        return name;
    }
    
    @Override
    public String toString() {
        return "Column(amount: " + amount + ", name: " + name + ")";
    }
    
    // 使用示例
    public static void main(String[] args) {
        // 假设有这样的对象
        class A {
            Object amount;
            A(Object amount) { this.amount = amount; }
        }
        
        class B {
            String name;
            B(String name) { this.name = name; }
        }
        
        A a = new A(100);
        B b = new B("示例列");
        
        // 可以这样创建：
        Column column = new Column(a.amount, b.name);
        System.out.println(column);
    }
}
