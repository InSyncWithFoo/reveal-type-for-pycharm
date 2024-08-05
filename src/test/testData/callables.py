from collections.abc import Callable

def f1(a: int, /, b: bytes, *, c: str) -> float: ...

reveal_type(<weak_warning descr="Revealed type is \\"function\\"">f1</weak_warning>)


f2: Callable[[int, bytes, str], float]

reveal_type(<weak_warning descr="Revealed type is \\"(int, bytes, str) -> float\\"">f2</weak_warning>)
