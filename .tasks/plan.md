[更改计划]
- 文件：math_utils.js, precision_demo.js
- 理由：
  - math_utils.js: 提供通用的浮点数运算修正工具，解决 JS 精度丢失问题。
  - precision_demo.js: 直观展示精度问题的现象及工具类的修复效果。

实施清单：
1. 创建 `math_utils.js`，实现 `add`, `sub`, `mul`, `div` 方法，核心采用字符串去点转整数法。
2. 创建 `precision_demo.js`，包含原生运算与工具类运算的对比输出。
3. 运行 `node precision_demo.js` 验证输出正确性。
