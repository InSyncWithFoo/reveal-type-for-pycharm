# FIXME: Remove this workaround when a mock SDK is configured.

class int:
	def __init__(self, v) -> None: ...

class str:
	def __init__(self, v) -> None: ...

class tuple[A, B]:
	def __init__(self, a: A, b: B) -> None: ...


class G[C, D]:
	
	def __init__(self, v: C, w: D): ...
	
	@property
	def c(self) -> C: ...
	
	@property
	def d(self) -> D: ...
	
	def m(self) -> tuple[C, D]: ...


g = G(int(20), str(''))

reveal_type(<weak_warning descr="Revealed type is \\"G\\"">g</weak_warning>)

reveal_type(<weak_warning descr="Revealed type is \\"int\\"">g.c</weak_warning>)
reveal_type(<weak_warning descr="Revealed type is \\"str\\"">g.d</weak_warning>)
reveal_type(<weak_warning descr="Revealed type is \\"tuple\\"">g.m()</weak_warning>)  # (int, str)
