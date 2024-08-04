# FIXME: Remove this workaround when a mock SDK is configured.

class int:
	def __init__(self, v) -> None: ...

class str:
	def __init__(self, v) -> None: ...

def main():
	a: int
	reveal_type(<weak_warning descr="Revealed type is \\"int\\"">a</weak_warning>)

	b = str('23')
	reveal_type(<weak_warning descr="Revealed type is \\"str\\"">b</weak_warning>)
