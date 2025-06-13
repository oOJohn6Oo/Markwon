## 题目：已知函数 $$f(x) = x^2 - 4x + 3$$

1. 求函数的顶点坐标；
2. 判断函数图像开口方向，并画出大致图像；
3. 解不等式：$$f(x) < 0$$

---

## 解答：

### 1. 顶点坐标

函数 $$f(x) = x^2 - 4x + 3$$ 是一个标准的二次函数。

使用顶点公式：

$$
x_0 = \frac{-b}{2a} = \frac{-(-4)}{2 \cdot 1} = \frac{4}{2} = 2
$$

$$
y_0 = f(2) = 2^2 - 4 \cdot 2 + 3 = 4 - 8 + 3 = -1
$$

**所以顶点坐标为：$$(2, -1)$$**

---

### 2. 图像开口方向

因为系数 $$a = 1 > 0$$，所以**抛物线开口向上**。

该函数图像是一个开口向上的抛物线，顶点为最低点 $$(2, -1)$$。

---

### 3. 解不等式 $$f(x) < 0$$

我们先求函数的零点，即解方程：

$$
x^2 - 4x + 3 = 0
$$

解得：

$$
x = \frac{4 \pm \sqrt{(-4)^2 - 4 \cdot 1 \cdot 3}}{2 \cdot 1} = \frac{4 \pm \sqrt{16 - 12}}{2} = \frac{4 \pm 2}{2}
$$

$$
\Rightarrow x = 1 \quad \text{或} \quad x = 3
$$

因为是开口向上的抛物线，所以在 $$x \in (1, 3)$$ 之间，函数值小于 0。

**所以不等式的解集是：**

$$
x \in (1, 3)
$$


## Latex style test

### Using LatexParseStyle.STYLE_DOLLAR

```
This is a inline $ x \in (1, 3) $ style test
This is a inline $$ x \in (1, 3) $$ style test
This is a inline $$$ x \in (1, 3) $$$ style test

This is a block style test

$
x \in (1, 3)
$

$$
x \in (1, 3)
$$

$$$
x \in (1, 3)
$$$

```

Let the price be \$x, then the total cost is given by $\C = n \cdot \text{\$}x$


This is a inline $ x \in (1, 3) $ style test
This is a inline $$ x \in (1, 3) $$ style test
This is a inline $$$ x \in (1, 3) $$$ style test

This is a block style test

$
x \in (1, 3)
$

$$
x \in (1, 3)
$$

$$$
x \in (1, 3)
$$$

Block style test end


### Using LatexParseStyle.STYLE_BRACKETS

This is a inline \(x \in (1, 3) \) style test

This is a block style test
\[
x \in (1, 3)
\]
Block style test end
