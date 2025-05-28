use std::collections::{HashMap, VecDeque};
use rand::seq::SliceRandom;
use rand::Rng;

#[derive(Clone, Debug, PartialEq)]
enum NodeType {
    Comb,
    Reg,
    Input,
    Output,
}

#[derive(Clone, Debug)]
enum OpType {
    And,
    Or,
    Not,
}

#[derive(Clone, Debug)]
struct DAG {
    adj_list: HashMap<String, Vec<String>>,
    node_type: HashMap<String, NodeType>,
    reg_init: HashMap<String, i32>,
    op_type: HashMap<String, OpType>,
    sig_val: HashMap<String, i32>,
    values: HashMap<String, i32>,
}

impl DAG {
    fn new() -> Self {
        DAG {
            adj_list: HashMap::new(),
            node_type: HashMap::new(),
            reg_init: HashMap::new(),
            op_type: HashMap::new(),
            sig_val: HashMap::new(),
            values: HashMap::new(),
        }
    }

    fn add_node(&mut self, node: &str, node_type: NodeType, reg_init: Option<i32>, op_type: Option<OpType>, sig_val: Option<i32>) {
        self.adj_list.insert(node.to_string(), Vec::new());
        self.node_type.insert(node.to_string(), node_type.clone());
        if let Some(val) = reg_init {
            self.reg_init.insert(node.to_string(), val);
        }
        if node_type == NodeType::Comb {
            if let Some(op) = op_type {
                self.op_type.insert(node.to_string(), op);
            }
        }
        if node_type == NodeType::Input {
            if let Some(sig) = sig_val {
                self.sig_val.insert(node.to_string(), sig);
            }
        }
    }

    fn add_edge(&mut self, src: &str, dst: &str) {
        self.adj_list.get_mut(src).unwrap().push(dst.to_string());
    }

    fn get_parent_keys(&self, target_node: &str) -> Vec<String> {
        self.adj_list.iter()
            .filter_map(|(k, v)| if v.contains(&target_node.to_string()) { Some(k.clone()) } else { None })
            .collect()
    }

    fn record_out(&mut self, node_out_val: &HashMap<String, i32>) -> HashMap<String, i32> {
        let mut result = Vec::new();
        let mut node_out_val_updated = node_out_val.clone();

        for key in self.adj_list.keys() {
            if node_out_val.contains_key(key) && self.node_type[key] != NodeType::Reg {
                continue;
            }
            let parents: Vec<String> = self.adj_list.iter()
                .filter_map(|(parent, children)| if children.contains(key) { Some(parent.clone()) } else { None })
                .collect();
            if parents.iter().all(|p| node_out_val.contains_key(p)) {
                result.push(key.clone());
            }
        }

        for node in result {
            let mut parent_keys = self.get_parent_keys(&node);

            if parent_keys.is_empty() && self.node_type[&node] == NodeType::Reg {
                self.values.insert(node.clone(), self.reg_init[&node]);
                continue;
            }
            if self.node_type[&node] == NodeType::Output || self.node_type[&node] == NodeType::Reg {
                if let Some(parent) = parent_keys.pop() {
                    self.values.insert(node.clone(), node_out_val[&parent]);
                }
            } else {
                let val_a = node_out_val[&parent_keys.pop().unwrap()];
                match self.op_type[&node] {
                    OpType::Not => {
                        node_out_val_updated.insert(node.clone(), if val_a == 1 { 0 } else { 1 });
                    }
                    OpType::And => {
                        let val_b = node_out_val[&parent_keys.pop().unwrap()];
                        node_out_val_updated.insert(node.clone(), val_a & val_b);
                    }
                    OpType::Or => {
                        let val_b = node_out_val[&parent_keys.pop().unwrap()];
                        node_out_val_updated.insert(node.clone(), val_a | val_b);
                    }
                }
            }
        }
        node_out_val_updated
    }

    fn topological_sort_with_levels(&mut self) -> HashMap<String, usize> {
        let mut inputs: HashMap<String, usize> = self.adj_list.keys().map(|k| (k.clone(), 0)).collect();
        for node in self.adj_list.keys() {
            for neighbor in &self.adj_list[node] {
                *inputs.get_mut(neighbor).unwrap() += 1;
            }
        }

        let mut levels: HashMap<String, usize> = HashMap::new();
        let mut queue: VecDeque<String> = VecDeque::new();
        let mut node_in_level: Vec<String> = Vec::new();
        let mut node_out_val: HashMap<String, i32> = HashMap::new();

        for node in self.adj_list.keys() {
            if self.node_type[node] == NodeType::Reg || self.node_type[node] == NodeType::Input {
                levels.insert(node.clone(), 0);
                queue.push_back(node.clone());
                node_in_level.push(node.clone());
            }
        }

        while let Some(current) = queue.pop_front() {
            node_in_level.remove(0);
            let current_level = levels[&current];

            if current_level == 0 {
                if self.node_type[&current] == NodeType::Reg {
                    node_out_val.insert(current.clone(), self.reg_init[&current]);
                }
                if self.node_type[&current] == NodeType::Input {
                    node_out_val.insert(current.clone(), self.sig_val[&current]);
                }
            }

            for neighbor in &self.adj_list[&current] {
                *inputs.get_mut(neighbor).unwrap() -= 1;
                if inputs[neighbor] == 0 && self.node_type[neighbor] != NodeType::Reg {
                    levels.insert(neighbor.clone(), current_level + 1);
                    queue.push_back(neighbor.clone());
                }
            }

            if node_in_level.is_empty() {
                node_in_level = queue.iter().cloned().collect();
                self.record_out(&node_out_val);
                node_out_val = self.record_out(&node_out_val);
            }
        }
        levels
    }

    fn display(&self) {
        println!("\nAdjacency List:");
        for (node, neighbors) in &self.adj_list {
            let mut node_info = format!("{:?}", self.node_type[node]);
            match self.node_type[node] {
                NodeType::Reg => node_info += &format!(", init={}", self.reg_init[node]),
                NodeType::Comb => node_info += &format!(", gate_type={:?}", self.op_type[node]),
                NodeType::Input => node_info += &format!(", sig_val={}", self.sig_val[node]),
                _ => {}
            }
            println!("{} ({}): -> {:?}", node, node_info, neighbors);
        }
    }
}


fn create_example_circuit() -> DAG {
    let mut circuit = DAG::new();

    circuit.add_node("R1", NodeType::Reg, Some(1), None, None);
    circuit.add_node("R2", NodeType::Reg, Some(1), None, None);
    circuit.add_node("R3", NodeType::Reg, Some(1), None, None);

    circuit.add_node("in", NodeType::Input, None, None, Some(1));
    circuit.add_node("out", NodeType::Output, None, None, None);

    circuit.add_node("A", NodeType::Comb, None, Some(OpType::And), None);
    circuit.add_node("B", NodeType::Comb, None, Some(OpType::Or), None);
    circuit.add_node("C", NodeType::Comb, None, Some(OpType::And), None);
    circuit.add_node("D", NodeType::Comb, None, Some(OpType::Not), None);
    circuit.add_node("E", NodeType::Comb, None, Some(OpType::Not), None);

    circuit.add_edge("R1", "A");
    circuit.add_edge("in", "A");
    circuit.add_edge("R2", "B");
    circuit.add_edge("A", "B");
    circuit.add_edge("B", "C");
    circuit.add_edge("R3", "C");
    circuit.add_edge("C", "R3");
    circuit.add_edge("R3", "D");
    circuit.add_edge("R3", "E");
    circuit.add_edge("D", "out");
    circuit.add_edge("E", "R2");

    circuit
}

// Random circuit generation (simplified for Rust)
fn create_random_circuit(
    num_inputs: usize,
    num_outputs: usize,
    num_regs: usize,
    num_comb: usize,
    max_depth: usize,
) -> DAG {
    let mut circuit = DAG::new();
    let mut rng = rand::thread_rng();

    let inputs: Vec<String> = (0..num_inputs).map(|i| format!("in{}", i)).collect();
    let outputs: Vec<String> = (0..num_outputs).map(|i| format!("out{}", i)).collect();
    let regs: Vec<String> = (0..num_regs).map(|i| format!("reg{}", i)).collect();
    let combs: Vec<String> = (0..num_comb).map(|i| format!("gate{}", i)).collect();

    for node in &inputs {
        circuit.add_node(node, NodeType::Input, None, None, Some(rng.gen_range(0..=1)));
    }
    for node in &regs {
        circuit.add_node(node, NodeType::Reg, Some(rng.gen_range(0..=1)), None, None);
    }
    for node in &outputs {
        circuit.add_node(node, NodeType::Output, None, None, None);
    }

    let mut node_levels: HashMap<String, usize> = HashMap::new();
    let mut available_nodes = inputs.clone();
    available_nodes.extend(regs.clone());

    for node in &combs {
        let level = rng.gen_range(1..=max_depth);
        node_levels.insert(node.clone(), level);

        let gate_type = match rng.gen_range(0..3) {
            0 => OpType::Not,
            1 => OpType::And,
            _ => OpType::Or,
        };
        let num_parents = if let OpType::Not = gate_type { 1 } else { 2 };

        let eligible_parents: Vec<String> = available_nodes.iter()
            .filter(|n| node_levels.get(*n).unwrap_or(&0) < &level)
            .cloned()
            .collect();

        if eligible_parents.is_empty() {
            continue;
        }
        let parents = eligible_parents.choose_multiple(&mut rng, num_parents).cloned().collect::<Vec<_>>();

        circuit.add_node(node, NodeType::Comb, None, Some(gate_type), None);
        for parent in &parents {
            circuit.add_edge(parent, node);
        }
        available_nodes.push(node.clone());
    }

    for output in &outputs {
        if !available_nodes.is_empty() {
            let source = available_nodes.choose(&mut rng).unwrap().clone();
            circuit.add_edge(&source, output);
        }
    }

    for reg in &regs {
        if !combs.is_empty() {
            let source = combs.choose(&mut rng).unwrap().clone();
            circuit.add_edge(&source, reg);
        }
    }

    circuit
}

// --- main logic ---
fn main() {
    // let mut circuit = create_random_circuit(2, 2, 3, 8, 4);
    let mut circuit = create_example_circuit();
    circuit.display();

    println!("\nLevelization:");
    let levels = circuit.topological_sort_with_levels();
    let max_level = *levels.values().max().unwrap_or(&0);
    for level in 0..=max_level {
        let nodes_at_level: Vec<_> = levels.iter().filter(|&(_, &lvl)| lvl == level).map(|(n, _)| n.clone()).collect();
        println!("Level {}: {:?}", level, nodes_at_level);
    }

    println!("\nCircuit Output:");
    println!("{:?}", circuit.values);
}
