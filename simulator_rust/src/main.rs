use petgraph::graph::{Graph, NodeIndex, EdgeIndex};
use petgraph::Direction::{Incoming, Outgoing};
use petgraph::visit::EdgeRef;
use std::collections::{HashMap, VecDeque};
use std::fmt;

#[derive(Debug, Clone, PartialEq)]
pub enum NodeType {
    Comb,
    Reg,
    Input,
    Output,
    Sram,
}

#[derive(Debug, Clone, PartialEq)]
pub enum OpType {
    Not,
    And,
    Or,
}

#[derive(Debug, Clone, PartialEq)]
pub enum Value {
    X,
    Bit(u8),
}

impl fmt::Display for Value {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            Value::X => write!(f, "X"),
            Value::Bit(b) => write!(f, "{}", b),
        }
    }
}

impl Value {
    pub fn from_str(s: &str) -> Self {
        match s {
            "X" => Value::X,
            "0" => Value::Bit(0),
            "1" => Value::Bit(1),
            _ => Value::X,
        }
    }

    pub fn to_int(&self) -> Option<u8> {
        match self {
            Value::Bit(b) => Some(*b),
            Value::X => None,
        }
    }
}

#[derive(Debug, Clone)]
pub struct RtlNode {
    pub name: String,
    pub node_type: NodeType,
    pub reg_init: Option<Value>,
    pub op_type: Option<OpType>,
    pub sig_val: Option<Value>,
    
    // SRAM-specific fields
    pub sram_memory: Option<Vec<Value>>,
    pub sram_read_latency: Option<usize>,
    pub sram_rdaddr_buffer: Option<Vec<Option<usize>>>,
    pub sram_read_addr: Option<usize>,
    pub sram_write_addr: Option<usize>,
}

impl RtlNode {
    pub fn new_input(name: String, sig_val: Value) -> Self {
        Self {
            name,
            node_type: NodeType::Input,
            reg_init: None,
            op_type: None,
            sig_val: Some(sig_val),
            sram_memory: None,
            sram_read_latency: None,
            sram_rdaddr_buffer: None,
            sram_read_addr: None,
            sram_write_addr: None,
        }
    }

    pub fn new_reg(name: String, reg_init: Value) -> Self {
        Self {
            name,
            node_type: NodeType::Reg,
            reg_init: Some(reg_init),
            op_type: None,
            sig_val: None,
            sram_memory: None,
            sram_read_latency: None,
            sram_rdaddr_buffer: None,
            sram_read_addr: None,
            sram_write_addr: None,
        }
    }

    pub fn new_output(name: String) -> Self {
        Self {
            name,
            node_type: NodeType::Output,
            reg_init: None,
            op_type: None,
            sig_val: None,
            sram_memory: None,
            sram_read_latency: None,
            sram_rdaddr_buffer: None,
            sram_read_addr: None,
            sram_write_addr: None,
        }
    }

    pub fn new_comb(name: String, op_type: OpType) -> Self {
        Self {
            name,
            node_type: NodeType::Comb,
            reg_init: None,
            op_type: Some(op_type),
            sig_val: None,
            sram_memory: None,
            sram_read_latency: None,
            sram_rdaddr_buffer: None,
            sram_read_addr: None,
            sram_write_addr: None,
        }
    }

    pub fn new_sram(
        name: String,
        memory: Vec<Value>,
        read_latency: usize,
        read_addr: Option<usize>,
        write_addr: Option<usize>,
    ) -> Self {
        let rdaddr_buffer = vec![None; read_latency];
        Self {
            name,
            node_type: NodeType::Sram,
            reg_init: None,
            op_type: None,
            sig_val: None,
            sram_memory: Some(memory),
            sram_read_latency: Some(read_latency),
            sram_rdaddr_buffer: Some(rdaddr_buffer),
            sram_read_addr: read_addr,
            sram_write_addr: write_addr,
        }
    }
    
}

#[derive(Debug, Clone)]
pub struct RtlEdge;

type RtlGraph = Graph<RtlNode, RtlEdge>;

#[derive(Debug)]
pub struct RtlSimulator {
    pub graph: RtlGraph,
    pub node_map: HashMap<String, NodeIndex>,
    pub values: HashMap<NodeIndex, Value>,
}

impl RtlSimulator {
    pub fn new() -> Self {
        Self {
            graph: RtlGraph::new(),
            node_map: HashMap::new(),
            values: HashMap::new(),
        }
    }

    pub fn add_node(&mut self, node: RtlNode) -> NodeIndex {
        let name = node.name.clone();
        let node_idx = self.graph.add_node(node);
        self.node_map.insert(name, node_idx);
        node_idx
    }

    pub fn add_edge(&mut self, src_name: &str, dst_name: &str) -> Result<EdgeIndex, String> {
        let src_idx = self.node_map.get(src_name)
            .ok_or_else(|| format!("Source node '{}' not found", src_name))?;
        let dst_idx = self.node_map.get(dst_name)
            .ok_or_else(|| format!("Destination node '{}' not found", dst_name))?;
        
        Ok(self.graph.add_edge(*src_idx, *dst_idx, RtlEdge))
    }

    fn get_parents(&self, node_idx: NodeIndex) -> Vec<NodeIndex> {
        self.graph.edges_directed(node_idx, Incoming)
            .map(|edge| edge.source())
            .collect()
    }

    fn record_out(&mut self, mut node_out_val: HashMap<NodeIndex, Value>) -> HashMap<NodeIndex, Value> {
        let mut result = Vec::new();

        // Find nodes whose parents have been evaluated (except reg and sram)
        for node_idx in self.graph.node_indices() {
            let node = &self.graph[node_idx];
            
            if node_out_val.contains_key(&node_idx) && 
               node.node_type != NodeType::Reg && 
               node.node_type != NodeType::Sram {
                continue;
            }

            let parents = self.get_parents(node_idx);
            if parents.iter().all(|parent| node_out_val.contains_key(parent)) {
                result.push(node_idx);
            }
        }

        // Perform combinational logic
        for node_idx in result {
            let node = &self.graph[node_idx].clone();
            let parents = self.get_parents(node_idx);

            match node.node_type {
                NodeType::Reg => {
                    if parents.is_empty() {
                        let val = node.reg_init.clone().unwrap_or(Value::X);
                        self.values.insert(node_idx, val.clone());
                        node_out_val.insert(node_idx, val);
                    } else {
                        let parent_val = node_out_val[&parents[0]].clone();
                        self.values.insert(node_idx, parent_val.clone());
                        node_out_val.insert(node_idx, parent_val);
                    }
                }
                NodeType::Output => {
                    if !parents.is_empty() {
                        let val = node_out_val[&parents[0]].clone();
                        self.values.insert(node_idx, val.clone());
                        node_out_val.insert(node_idx, val);
                    }
                }
                NodeType::Comb => {
                    let val = self.compute_comb_logic(&node, &parents, &node_out_val);
                    node_out_val.insert(node_idx, val);
                }
                NodeType::Sram => {
                    if !parents.is_empty() {
                        // Handle SRAM write
                        let write_val = node_out_val[&parents[0]].clone();
                        if let Some(write_addr) = node.sram_write_addr {
                            // Update the node in the graph with new memory state
                            let mut updated_node = node.clone();
                            if let Some(ref mut memory) = updated_node.sram_memory {
                                if write_addr < memory.len() {
                                    memory[write_addr] = write_val;
                                }
                            }
                            self.graph[node_idx] = updated_node;
                        }
                    }
                }
                _ => {}
            }
        }

        node_out_val
    }

    fn compute_comb_logic(
        &self,
        node: &RtlNode,
        parents: &[NodeIndex],
        node_out_val: &HashMap<NodeIndex, Value>,
    ) -> Value {
        match node.op_type.as_ref().unwrap() {
            OpType::Not => {
                if parents.is_empty() {
                    return Value::X;
                }
                let val_a = &node_out_val[&parents[0]];
                match val_a {
                    Value::X => Value::X,
                    Value::Bit(b) => Value::Bit(if *b == 1 { 0 } else { 1 }),
                }
            }
            OpType::And => {
                if parents.len() < 2 {
                    return Value::X;
                }
                let val_a = &node_out_val[&parents[0]];
                let val_b = &node_out_val[&parents[1]];
                match (val_a, val_b) {
                    (Value::X, _) | (_, Value::X) => Value::X,
                    (Value::Bit(a), Value::Bit(b)) => Value::Bit(a & b),
                }
            }
            OpType::Or => {
                if parents.len() < 2 {
                    return Value::X;
                }
                let val_a = &node_out_val[&parents[0]];
                let val_b = &node_out_val[&parents[1]];
                match (val_a, val_b) {
                    (Value::X, Value::X) => Value::X,
                    (Value::X, Value::Bit(b)) => Value::Bit(*b),
                    (Value::Bit(a), Value::X) => Value::Bit(*a),
                    (Value::Bit(a), Value::Bit(b)) => Value::Bit(a | b),
                }
            }
        }
    }

    pub fn topological_sort_with_levels(&mut self) -> HashMap<NodeIndex, usize> {
    let mut inputs = HashMap::new();
    let mut levels = HashMap::new();
    let mut queue = VecDeque::new();
    let mut node_out_val = HashMap::new();

    // Initialize input counts
    for node_idx in self.graph.node_indices() {
        inputs.insert(node_idx, self.get_parents(node_idx).len());
    }

    // Set level 0 for regs, inputs, and srams
    for node_idx in self.graph.node_indices() {
        let node = &self.graph[node_idx];
        match node.node_type {
            NodeType::Reg | NodeType::Input | NodeType::Sram => {
                levels.insert(node_idx, 0);
                queue.push_back(node_idx);
            }
            _ => {}
        }
    }

    // Process nodes level by level
    while !queue.is_empty() {
        let current_level_size = queue.len();
        let mut current_level_nodes = Vec::new();

        // Process all nodes at current level
        for _ in 0..current_level_size {
            let current = queue.pop_front().unwrap();
            current_level_nodes.push(current);
            let current_level = levels[&current];

            // Get node data first (immutable borrow)
            let node_type = self.graph[current].node_type.clone();
            let reg_init = self.graph[current].reg_init.clone();
            let sig_val = self.graph[current].sig_val.clone();
            
            // Initialize values for level 0 nodes
            if current_level == 0 {
                match node_type {
                    NodeType::Input => {
                        if let Some(sig_val) = sig_val {
                            node_out_val.insert(current, sig_val);
                        }
                    }
                    NodeType::Reg => {
                        let parents = self.get_parents(current);
                        if !parents.is_empty() && 
                           self.graph[parents[0]].node_type == NodeType::Input {
                            if let Some(sig_val) = &self.graph[parents[0]].sig_val {
                                node_out_val.insert(current, sig_val.clone());
                            }
                        } else if let Some(reg_init) = reg_init {
                            node_out_val.insert(current, reg_init);
                        }
                    }
                    NodeType::Sram => {
                        // Handle SRAM read - need mutable access
                        let node = &mut self.graph[current];
                        if let Some(memory) = &node.sram_memory {
                            if node.sram_read_latency == Some(0) {
                                // Zero latency: direct read from memory at read_addr
                                let val = if let Some(read_addr) = node.sram_read_addr {
                                    if read_addr < memory.len() {
                                        memory[read_addr].clone()
                                    } else {
                                        Value::X
                                    }
                                } else {
                                    Value::X
                                };
                                node_out_val.insert(current, val);
                            } else if let Some(buffer) = &mut node.sram_rdaddr_buffer {
                                if !buffer.is_empty() {
                                    // Normal latency case - use buffer
                                    let current_addr = buffer.remove(0);
                                    let val = if let Some(addr) = current_addr {
                                        if addr < memory.len() {
                                            memory[addr].clone()
                                        } else {
                                            Value::X
                                        }
                                    } else {
                                        Value::X
                                    };
                                    node_out_val.insert(current, val);

                                    // Update buffer
                                    if let Some(read_addr) = node.sram_read_addr {
                                        buffer.push(Some(read_addr));
                                    } else {
                                        buffer.push(None);
                                    }
                                } else {
                                    node_out_val.insert(current, Value::X);
                                }
                            } else {
                                node_out_val.insert(current, Value::X);
                            }
                        }
                    }
                    _ => {}
                }
            }

            // Update children's input counts and levels
            for edge in self.graph.edges_directed(current, Outgoing) {
                let neighbor = edge.target();
                if let Some(count) = inputs.get_mut(&neighbor) {
                    if *count > 0 {  // Add this check to prevent underflow
                        *count -= 1;
                        if *count == 0 && self.graph[neighbor].node_type != NodeType::Reg {
                            levels.insert(neighbor, current_level + 1);
                            queue.push_back(neighbor);
                        }
                    }
                }
            }
        }

        // Perform combinational logic for current level
        if !current_level_nodes.is_empty() {
            node_out_val = self.record_out(node_out_val);
        }
    }

    levels
}


    pub fn display(&self) {
        println!("\nAdjacency List:");
        for node_idx in self.graph.node_indices() {
            let node = &self.graph[node_idx];
            let neighbors: Vec<String> = self.graph.edges_directed(node_idx, Outgoing)
                .map(|edge| self.graph[edge.target()].name.clone())
                .collect();
            
            let mut node_info = format!("{:?}", node.node_type);
            match node.node_type {
                NodeType::Reg => {
                    if let Some(init) = &node.reg_init {
                        node_info.push_str(&format!(", init={}", init));
                    }
                }
                NodeType::Comb => {
                    if let Some(op) = &node.op_type {
                        node_info.push_str(&format!(", gate_type={:?}", op));
                    }
                }
                NodeType::Input => {
                    if let Some(sig) = &node.sig_val {
                        node_info.push_str(&format!(", sig_val={}", sig));
                    }
                }
                NodeType::Sram => {
                    if let Some(latency) = node.sram_read_latency {
                        node_info.push_str(&format!(", latency={}", latency));
                    }
                }
                _ => {}
            }
            
            println!("{} ({}): -> {:?}", node.name, node_info, neighbors);
        }
    }
}

// Example usage and test functions
pub fn create_example_circuit() -> RtlSimulator {
    let mut circuit = RtlSimulator::new();
    
    // Add nodes
    circuit.add_node(RtlNode::new_reg("R1".to_string(), Value::X));
    circuit.add_node(RtlNode::new_reg("R2".to_string(), Value::Bit(1)));
    circuit.add_node(RtlNode::new_reg("R3".to_string(), Value::Bit(1)));
    
    circuit.add_node(RtlNode::new_input("in1".to_string(), Value::Bit(1)));
    circuit.add_node(RtlNode::new_input("in2".to_string(), Value::Bit(1)));
    circuit.add_node(RtlNode::new_output("out".to_string()));
    
    circuit.add_node(RtlNode::new_comb("A".to_string(), OpType::And));
    circuit.add_node(RtlNode::new_comb("B".to_string(), OpType::Or));
    circuit.add_node(RtlNode::new_comb("C".to_string(), OpType::And));
    circuit.add_node(RtlNode::new_comb("D".to_string(), OpType::Not));
    circuit.add_node(RtlNode::new_comb("E".to_string(), OpType::Not));
    
    // Add edges
    let _ = circuit.add_edge("in1", "R1");
    let _ = circuit.add_edge("R1", "A");
    let _ = circuit.add_edge("in2", "A");
    let _ = circuit.add_edge("R2", "B");
    let _ = circuit.add_edge("A", "B");
    let _ = circuit.add_edge("B", "C");
    let _ = circuit.add_edge("R3", "C");
    let _ = circuit.add_edge("C", "R3");
    let _ = circuit.add_edge("R3", "D");
    let _ = circuit.add_edge("R3", "E");
    let _ = circuit.add_edge("D", "out");
    let _ = circuit.add_edge("E", "R2");
    
    circuit
}

pub fn create_sram_circuit() -> RtlSimulator {
    let mut circuit = RtlSimulator::new();
    
    // Read only
    circuit.add_node(RtlNode::new_sram(
        "sram1".to_string(),
        vec![Value::Bit(1), Value::Bit(2), Value::Bit(3), Value::Bit(4)],
        0,
        Some(2),
        None,
    ));
    circuit.add_node(RtlNode::new_output("out_data".to_string()));
    let _ = circuit.add_edge("sram1", "out_data");

    // // Write only
    // circuit.add_node(RtlNode::new_sram(
    //     "sram1".to_string(),
    //     vec![Value::Bit(1), Value::Bit(2), Value::Bit(3), Value::Bit(4)],
    //     0,
    //     None,
    //     Some(2),
    // ));
    // circuit.add_node(RtlNode::new_input("in1".to_string(), Value::Bit(5)));
    // let _ = circuit.add_edge("in1", "sram1");

    // // Read and write with same/diff addr
    // circuit.add_node(RtlNode::new_sram(
    //     "sram1".to_string(),
    //     vec![Value::Bit(1), Value::Bit(2), Value::Bit(3), Value::Bit(4)],
    //     3,
    //     Some(3),
    //     Some(2),
    // ));
    // circuit.add_node(RtlNode::new_input("in1".to_string(), Value::Bit(5)));
    // circuit.add_node(RtlNode::new_output("out1".to_string()));
    // let _ = circuit.add_edge("in1", "sram1");
    // let _ = circuit.add_edge("sram1", "out1");

    // // Comb logic going into write / loop
    // circuit.add_node(RtlNode::new_sram(
    //     "sram1".to_string(),
    //     vec![Value::X, Value::Bit(1), Value::X, Value::X],
    //     3,
    //     Some(1),
    //     Some(2),
    // ));
    // circuit.add_node(RtlNode::new_input("in1".to_string(), Value::Bit(1)));
    // circuit.add_node(RtlNode::new_comb("A".to_string(), OpType::And));
    // circuit.add_node(RtlNode::new_comb("B".to_string(), OpType::Not));
    // circuit.add_node(RtlNode::new_reg("R1".to_string(), Value::Bit(1)));
    // let _ = circuit.add_edge("in1", "A");
    // let _ = circuit.add_edge("R1", "A");
    // let _ = circuit.add_edge("A", "sram1");
    // let _ = circuit.add_edge("sram1", "B");
    // let _ = circuit.add_edge("B", "R1");

    // // Comb logic going out of read / loop
    // circuit.add_node(RtlNode::new_sram(
    //     "sram1".to_string(),
    //     vec![Value::X, Value::Bit(1), Value::X, Value::X],
    //     3,
    //     Some(2),
    //     Some(2),
    // ));
    // circuit.add_node(RtlNode::new_input("in1".to_string(), Value::Bit(1)));
    // circuit.add_node(RtlNode::new_comb("A".to_string(), OpType::And));
    // circuit.add_node(RtlNode::new_comb("B".to_string(), OpType::Not));
    // circuit.add_node(RtlNode::new_reg("R1".to_string(), Value::Bit(0)));
    // let _ = circuit.add_edge("B", "sram1");
    // let _ = circuit.add_edge("R1", "B");
    // let _ = circuit.add_edge("sram1", "A");
    // let _ = circuit.add_edge("in1", "A");
    // let _ = circuit.add_edge("A", "R1");

    circuit
}

fn main() {
    let mut circuit = create_sram_circuit();
    circuit.display();

    println!("\nLevelization:");
    let levels = circuit.topological_sort_with_levels();
    let max_level = levels.values().max().unwrap_or(&0);
    
    for level in 0..=*max_level {
        let nodes_at_level: Vec<String> = levels.iter()
            .filter(|&(_, &lvl)| lvl == level)  // Fixed: added & before the tuple
            .map(|(&node_idx, _)| circuit.graph[node_idx].name.clone())
            .collect();
        println!("Level {}: {:?}", level, nodes_at_level);
    }

    println!("\nCircuit Output:");
    for (node_idx, value) in &circuit.values {
        let node_name = &circuit.graph[*node_idx].name;
        println!("{}: {}", node_name, value);
    }

    println!("\nSRAM Memory:");
    for node_idx in circuit.graph.node_indices() {
        let node = &circuit.graph[node_idx];
        if node.node_type == NodeType::Sram {
            if let Some(ref memory) = node.sram_memory {
                println!("{}: {:?}", node.name, memory);
            }
        }
    }

    println!("\nSRAM Address Buffer:");
    for node_idx in circuit.graph.node_indices() {
        let node = &circuit.graph[node_idx];
        if node.node_type == NodeType::Sram {
            if let Some(ref buffer) = node.sram_rdaddr_buffer {
                println!("{}: {:?}", node.name, buffer);
            }
        }
    }
}
