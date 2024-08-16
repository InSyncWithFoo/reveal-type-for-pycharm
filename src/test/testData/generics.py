class G[C, D]:
	
	def __init__(self, v: C, w: D): ...
	
	@property
	def c(self) -> C: ...
	
	@property
	def d(self) -> D: ...
	
	def m(self) -> tuple[C, D]: ...


g = G(int(20), str(''))

reveal_type(<weak_warning descr="Revealed type is \\"G[int, str]\\"">g</weak_warning>)

reveal_type(<weak_warning descr="Revealed type is \\"int\\"">g.c</weak_warning>)
reveal_type(<weak_warning descr="Revealed type is \\"str\\"">g.d</weak_warning>)
reveal_type(<weak_warning descr="Revealed type is \\"tuple[int, str]\\"">g.m()</weak_warning>)  # Also (int, str)


class G2[A, B]:
	def __init__(self, a: A, b: B | None = None) -> None: ...


g2 = G2(42)

reveal_type(<weak_warning descr="Revealed type is \\"G2[int, Any]\\"">g2</weak_warning>)
