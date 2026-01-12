const MathUtils = require('./math_utils');

console.log("=== JavaScript 精度问题演示 ===");

console.log("\n1. 加法 (0.1 + 0.2)");
console.log("原生结果:", 0.1 + 0.2);
console.log("期望结果:", 0.3);
console.log("MathUtils:", MathUtils.add(0.1, 0.2));

console.log("\n2. 乘法 (19.9 * 100)");
console.log("原生结果:", 19.9 * 100);
console.log("期望结果:", 1990);
console.log("MathUtils:", MathUtils.mul(19.9, 100));

console.log("\n3. 除法 (0.3 / 0.1)");
console.log("原生结果:", 0.3 / 0.1);
console.log("期望结果:", 3);
console.log("MathUtils:", MathUtils.div(0.3, 0.1));

console.log("\n4. 减法 (1.0 - 0.9)");
console.log("原生结果:", 1.0 - 0.9);
console.log("期望结果:", 0.1);
console.log("MathUtils:", MathUtils.sub(1.0, 0.9));

console.log("\n=== 演示结束 ===");
