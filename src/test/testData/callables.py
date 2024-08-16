from collections.abc import Callable

def f1(a: int, /, b: bytes, *, c: str) -> float: ...

# https://youtrack.jetbrains.com/issue/PY-60182
# Also (int, /, b: bytes, *, c: str) -> float
reveal_type(<weak_warning descr="Revealed type is \\"(a: int, Any, b: bytes, Any, c: str) -> float\\"">f1</weak_warning>)


f2: Callable[[int, bytes, str], float]

# Also Callable[[int, bytes, str], float]
reveal_type(<weak_warning descr="Revealed type is \\"(int, bytes, str) -> float\\"">f2</weak_warning>)


def f3[T: str](v: type[T]) -> T: ...

# Also Callable[[Type[T]], T] / (v: Type[T]) -> T
reveal_type(<weak_warning descr="Revealed type is \\"(v: Type[T ≤: str]) -> T ≤: str\\"">f3</weak_warning>)
