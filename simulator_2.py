class DAG:
    def __init__(self):
        self.adj_list = {}
        self.node_type = {} # comb/reg/input/output
        self.reg_init = {} # initial value for regs
        self.op_type = {} # gate type for comb
        self.sig_val = {} # signal value for inputs
        self.values = {} # simulated values for outs and regs

    def add_node(self, node, node_type, reg_init=None, op_type=None, sig_val=None):
        self.adj_list[node] = []
        self.node_type[node] = node_type
        self.reg_init[node] = reg_init
        if node_type == "comb":
            self.op_type[node] = op_type
        if node_type == "input":
            self.sig_val[node] = sig_val

    def add_edge(self, src, dst):
        self.adj_list[src].append(dst)

    def record_out(self, node_out_val):
        result = []
        node_out_val_updated = node_out_val.copy()
        for key in self.adj_list:
            # skip keys already in b
            if key in node_out_val and self.node_type[key] is not 'reg':
                continue
            # find all parent keys that reference this key
            parents = [parent for parent in self.adj_list if key in self.adj_list[parent]]
            # check if all parents exist in b
            if all(parent in node_out_val for parent in parents):
                result.append(key)

        # perform comb logic
        for node in result:
            parent_keys = self.get_parent_keys(self.adj_list, node)
            if self.node_type[node] == "reg":
                # if the reg has no input and only has init val
                if len(parent_keys) == 0:
                    self.values[node] = self.reg_init[node]
                    node_out_val_updated[node] = self.reg_init[node]
                    continue

                # if the reg has input and is initiallized to X
                else:
                    parent_val = node_out_val[parent_keys.pop()]
                    self.values[node] = parent_val
                    node_out_val_updated[node] = parent_val
                    continue

            if self.node_type[node] == 'output':
                # Output just takes its input (could be 'X')
                val = node_out_val[parent_keys.pop()]
                self.values[node] = val
                node_out_val_updated[node] = val
                continue

            # Combinational logic with X propagation
            if self.node_type[node] == "comb":
                op = self.op_type[node]
                if op == 'not':
                    val_a = node_out_val[parent_keys.pop()]
                    if val_a == 'X':
                        node_out_val_updated[node] = 'X'
                    else:
                        node_out_val_updated[node] = 0 if int(val_a) == 1 else 1
                elif op == 'and':
                    val_a = node_out_val[parent_keys.pop()]
                    val_b = node_out_val[parent_keys.pop()]
                    if val_a == 'X' or val_b == 'X':
                        node_out_val_updated[node] = 'X'
                    else:
                        node_out_val_updated[node] = int(val_a) & int(val_b)
                elif op == 'or':
                    val_a = node_out_val[parent_keys.pop()]
                    val_b = node_out_val[parent_keys.pop()]
                    if val_a == 'X' or val_b == 'X':
                        node_out_val_updated[node] = 'X'
                    else:
                        node_out_val_updated[node] = int(val_a) | int(val_b)
        return node_out_val_updated
    
    def get_parent_keys(self, a_dict, target_node):
        return [key for key, values in a_dict.items() if target_node in values]

    def topological_sort_with_levels(self):
        # set number of inputs for each node, will decrement
        inputs = {node: 0 for node in self.adj_list}
        for node in self.adj_list:
            for neighbor in self.adj_list[node]:
                inputs[neighbor] += 1
        
        levels = {}
        queue = []
        node_in_level = []
        node_out_val = {}
        
        # set regs and inputs to level 0
        for node in self.adj_list:
            if self.node_type[node] == "reg" or self.node_type[node] == "input":
                levels[node] = 0
                queue.append(node)
                node_in_level.append(node)
        
        # assign levels for rest of nodes
        while queue:
            current = queue.pop(0)
            node_in_level.pop(0)
            current_level = levels[current]

            # init regs and input signals
            if current_level == 0:
                if self.node_type[current] == "reg":
                    # Check if the register has a connected input
                    parents = [parent for parent in self.adj_list if current in self.adj_list[parent]]
                    if parents and self.node_type[parents[0]] == "input":
                        parent = parents[0]  # Assume single input
                        # Use input value if available, else fallback to reg_init
                        node_out_val[current] = self.sig_val[parent]
                    else:
                        node_out_val[current] = self.reg_init[current]
                elif self.node_type[current] == "input":
                    node_out_val[current] = self.sig_val[current]

            # mark path of current as walked
            for neighbor in self.adj_list[current]:
                inputs[neighbor] -= 1
                # see if children have all inputs walked
                # if walked, then set child to next level
                if inputs[neighbor] == 0 and self.node_type[neighbor] != "reg":
                    levels[neighbor] = current_level + 1
                    queue.append(neighbor)

            # level is full, perform logic
            if len(node_in_level) == 0:
                node_in_level = queue.copy()
                node_out_val = self.record_out(node_out_val)
        
        return levels

    def display(self):
        print("\nAdjacency List:")
        for node, neighbors in self.adj_list.items():
            node_info = f"{self.node_type[node]}"
            if self.node_type[node] == "reg":
                node_info += f", init={self.reg_init[node]}"
            elif self.node_type[node] == "comb":
                node_info += f", gate_type={self.op_type[node]}"
            elif self.node_type[node] == "input":
                node_info += f", sig_val={self.sig_val[node]}"
            print(f"{node} ({node_info}): -> {neighbors}")

def create_example_circuit():
    circuit = DAG()
    
    circuit.add_node("R1", "reg", reg_init="X")
    circuit.add_node("R2", "reg", reg_init="1")
    circuit.add_node("R3", "reg", reg_init="1")
    
    circuit.add_node("in1", "input", sig_val="1")
    circuit.add_node("in2", "input", sig_val="1")
    circuit.add_node("out", "output")
    
    circuit.add_node("A", "comb", op_type="and")
    circuit.add_node("B", "comb", op_type="or")
    circuit.add_node("C", "comb", op_type="and")
    circuit.add_node("D", "comb", op_type="not")
    circuit.add_node("E", "comb", op_type="not")
    
    circuit.add_edge("in1", "R1")
    circuit.add_edge("R1", "A")
    circuit.add_edge("in2", "A")
    circuit.add_edge("R2", "B")
    circuit.add_edge("A", "B")
    circuit.add_edge("B", "C")
    circuit.add_edge("R3", "C")
    circuit.add_edge("C", "R3")
    circuit.add_edge("R3", "D")
    circuit.add_edge("R3", "E")
    circuit.add_edge("D", "out")
    circuit.add_edge("E", "R2")
    
    return circuit

import random

def create_random_circuit(
    num_inputs=2,
    num_outputs=2,
    num_regs=3,
    num_comb=8,
    max_depth=4,
):
    circuit = DAG()
    
    # add nodes
    inputs = [f"in{i}" for i in range(num_inputs)]
    outputs = [f"out{i}" for i in range(num_outputs)]
    regs = [f"reg{i}" for i in range(num_regs)]
    combs = [f"gate{i}" for i in range(num_comb)]
    
    for node in inputs:
        circuit.add_node(node, "input", sig_val=str(random.choice([0, 1])))
    for node in regs:
        circuit.add_node(node, "reg", reg_init=str(random.choice([0, 1])))
    for node in outputs:
        circuit.add_node(node, "output")
    
    node_levels = {}
    available_nodes = inputs + regs.copy()
    
    for _, node in enumerate(combs):
        level = random.randint(1, max_depth)
        node_levels[node] = level
        
        # choose gate type
        gate_type = random.choice(["not", "and", "or"])
        if gate_type == "not":
            num_parents = 1 
        else:
            num_parents = 2

        # find eligible parents for node
        eligible_parents = []
        for n in available_nodes:
            if node_levels.get(n, 0) < level:
                eligible_parents.append(n)
        
        if len(eligible_parents) == 0:
            continue
        
        # choose randomly from eligible parents
        parents = random.sample(eligible_parents, min(num_parents, len(eligible_parents)))
        
        # connect parent to gate
        circuit.add_node(node, "comb", op_type=gate_type)
        for parent in parents:
            circuit.add_edge(parent, node)
            
        available_nodes.append(node)
    
    # connect outputs
    for output in outputs:
        if available_nodes:
            source = random.choice(available_nodes)
            circuit.add_edge(source, output)
    
    # for feedback paths to reg
    for reg in regs:
        if combs:
            source = random.choice(combs)
            circuit.add_edge(source, reg)
    
    return circuit

# circuit = create_random_circuit()
circuit = create_example_circuit()
circuit.display()

print("\nLevelization:")
levels = circuit.topological_sort_with_levels()
for level in range(max(levels.values()) + 1):
    nodes_at_level = [node for node, lvl in levels.items() if lvl == level]
    print(f"Level {level}: {nodes_at_level}")

print("\nCircuit Output:")
print(circuit.values)

