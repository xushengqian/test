# Python 实现
class Column:
    def __init__(self, amount, name):
        self.amount = amount
        self.name = name
    
    def __str__(self):
        return f"Column(amount: {self.amount}, name: {self.name})"
    
    def __repr__(self):
        return self.__str__()


# 使用示例
class A:
    def __init__(self, amount):
        self.amount = amount

class B:
    def __init__(self, name):
        self.name = name


if __name__ == "__main__":
    a = A(amount=100)
    b = B(name="示例列")
    
    # 可以这样创建：
    column = Column(a.amount, b.name)
    print(column)
