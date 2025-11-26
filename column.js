// JavaScript 实现
class Column {
  constructor(amount, name) {
    this.amount = amount;
    this.name = name;
  }

  toString() {
    return `Column(amount: ${this.amount}, name: ${this.name})`;
  }
}

// 使用示例
const a = { amount: 100 };
const b = { name: "示例列" };

// 可以这样创建：
const column = new Column(a.amount, b.name);
console.log(column.toString());

module.exports = Column;
