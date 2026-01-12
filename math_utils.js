/**
 * 解决 JavaScript 浮点数运算精度问题
 * 核心原理：将小数转换为整数进行运算
 */

const MathUtils = {
    /**
     * 获取小数位数
     */
    getDecimalLen(num) {
        try {
            return num.toString().split(".")[1].length;
        } catch (e) {
            return 0;
        }
    },

    /**
     * 加法
     * @param {number} arg1
     * @param {number} arg2
     */
    add(arg1, arg2) {
        let r1 = this.getDecimalLen(arg1);
        let r2 = this.getDecimalLen(arg2);
        let m = Math.pow(10, Math.max(r1, r2));
        // 使用 round 消除 arg * m 产生的微小误差
        return (Math.round(arg1 * m) + Math.round(arg2 * m)) / m;
    },

    /**
     * 减法
     * @param {number} arg1
     * @param {number} arg2
     */
    sub(arg1, arg2) {
        let r1 = this.getDecimalLen(arg1);
        let r2 = this.getDecimalLen(arg2);
        let m = Math.pow(10, Math.max(r1, r2));
        let n = (r1 >= r2) ? r1 : r2;
        // 使用 round 消除 arg * m 产生的微小误差
        return Number(((Math.round(arg1 * m) - Math.round(arg2 * m)) / m).toFixed(n));
    },

    /**
     * 乘法
     * @param {number} arg1
     * @param {number} arg2
     */
    mul(arg1, arg2) {
        let m = 0;
        let s1 = arg1.toString();
        let s2 = arg2.toString();
        try { m += s1.split(".")[1].length } catch (e) { }
        try { m += s2.split(".")[1].length } catch (e) { }
        return Number(s1.replace(".", "")) * Number(s2.replace(".", "")) / Math.pow(10, m);
    },

    /**
     * 除法
     * @param {number} arg1
     * @param {number} arg2
     */
    div(arg1, arg2) {
        let t1 = 0, t2 = 0, r1, r2;
        try { t1 = arg1.toString().split(".")[1].length } catch (e) { }
        try { t2 = arg2.toString().split(".")[1].length } catch (e) { }
        
        r1 = Number(arg1.toString().replace(".", ""));
        r2 = Number(arg2.toString().replace(".", ""));
        return (r1 / r2) * Math.pow(10, t2 - t1);
    }
};

module.exports = MathUtils;
