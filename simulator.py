class Signal:
    def __init__(self, initial=0):
        self.val = initial
        self.next_val = initial

    def set(self, val):
        self.next_val = val

    def update(self):
        self.val = self.next_val

    def __int__(self):
        return self.val

    def __str__(self):
        return str(self.val)


class Clock:
    def __init__(self):
        self.cycle = 0
        self.posedge = True

    def tick(self):
        self.posedge = not self.posedge
        if self.posedge:
            self.cycle += 1


class Simulator:
    def __init__(self):
        self.comb_processes = []
        self.seq_processes = []
        self.signals = []
        self.clock = Clock()

    def add_comb_process(self, process):
        self.comb_processes.append(process)

    def add_seq_process(self, process):
        self.seq_processes.append(process)

    def add_signal(self, signal):
        self.signals.append(signal)

    def run(self, cycles):
        for _ in range(cycles):
            for c in self.comb_processes:
                    c(self.clock)
            if self.clock.posedge:
                for s in self.seq_processes:
                    s(self.clock)
            self.clock.tick()


class Register:
    def __init__(self, clk, d, q, rst=None, enable=None):
        self.clk = clk
        self.d = d
        self.q = q
        self.rst = rst
        self.enable = enable

    def process(self, clock):
        if clock.posedge:
            if self.rst is not None and int(self.rst):
                self.q.set(0)
                self.q.update()
            elif self.enable is None or int(self.enable):
                self.q.set(int(self.d))
                self.q.update()


def and_gate(a, b, z):
    def process(clock):
        z.set(int(a) & int(b))
        z.update()
    return process

def or_gate(a, b, z):
    def process(clock):
        z.set(int(a) | int(b))
        z.update()
    return process

def mux2(sel, a, b, z):
    def process(clock):
        z.set(int(b) if int(sel) else int(a))
        z.update()
    return process


if __name__ == "__main__":

    d1 = Signal(0)  # d for reg1
    q1 = Signal(0)  # q for reg1
    en = Signal(1)  # enable for reg1,2
    rst = Signal(1)  # reset for reg1,2
    d2 = Signal(0)  # d for reg2
    q2 = Signal(0)  # q for reg2
    a = Signal(0)  # input to gate

    sim = Simulator()
    for s in [d1, q1, en, rst, d2, q2, a]:
        sim.add_signal(s)

    sim.add_comb_process(and_gate(a, q1, d2))

    reg1 = Register(sim.clock, d=d1, q=q1, rst=rst, enable=en)
    sim.add_seq_process(reg1.process)

    reg2 = Register(sim.clock, d=d2, q=q2, rst=rst, enable=en)
    sim.add_seq_process(reg2.process)

    for cycle in range(5):
        if cycle == 0:
            rst.set(0)
            rst.update()
        if cycle == 1:
            d1.set(1)
            d1.update()
        if cycle == 2:
            a.set(1)
            a.update()

        sim.run(1)
        print(f"Cycle {cycle}: a={a}, d1={d1}, q1={q1}, d2={d2}, q2={q2}")
