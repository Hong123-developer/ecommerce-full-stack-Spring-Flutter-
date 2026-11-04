import '../models/employee_model.dart';

/// Employee Provider - Mock API សម្រាប់ Employee CRUD
/// (នៅពេល Backend មាន endpoints ពិត អាចប្តូរទៅ Dio calls បាន)
class EmployeeProvider {
  // Simulated database
  final List<EmployeeModel> _employees = [
    EmployeeModel(
      id: 1,
      name: 'សុខា ចន្ថា',
      position: 'Software Developer',
      department: 'IT',
      salary: 1200,
      phone: '012345678',
      email: 'sokha@company.com',
    ),
    EmployeeModel(
      id: 2,
      name: 'វណ្ណៈ មករា',
      position: 'UI/UX Designer',
      department: 'Design',
      salary: 1000,
      phone: '012345679',
      email: 'vannak@company.com',
    ),
    EmployeeModel(
      id: 3,
      name: 'ស្រីពេជ្រ រតនា',
      position: 'HR Manager',
      department: 'HR',
      salary: 1500,
      phone: '012345680',
      email: 'sreypech@company.com',
    ),
    EmployeeModel(
      id: 4,
      name: 'រ៉ាវុធ សុផាត',
      position: 'Marketing Lead',
      department: 'Marketing',
      salary: 1300,
      phone: '012345681',
      email: 'ravuth@company.com',
    ),
    EmployeeModel(
      id: 5,
      name: 'ចាន់ដារា គង់វិចិត្រ',
      position: 'Accountant',
      department: 'Finance',
      salary: 1100,
      phone: '012345682',
      email: 'chandara@company.com',
    ),
  ];
  int _nextId = 6;

  /// Simulate network delay
  Future<void> _delay() => Future.delayed(const Duration(milliseconds: 800));

  /// Get all employees
  Future<List<EmployeeModel>> getEmployees() async {
    await _delay();
    return List.from(_employees);
  }

  /// Get employee by ID
  Future<EmployeeModel?> getEmployeeById(int id) async {
    await _delay();
    try {
      return _employees.firstWhere((e) => e.id == id);
    } catch (_) {
      return null;
    }
  }

  /// Create new employee
  Future<EmployeeModel> createEmployee(EmployeeModel employee) async {
    await _delay();
    final newEmployee = employee.copyWith(
      id: _nextId++,
      createdAt: DateTime.now().toIso8601String(),
    );
    _employees.add(newEmployee);
    return newEmployee;
  }

  /// Update employee
  Future<EmployeeModel> updateEmployee(int id, EmployeeModel employee) async {
    await _delay();
    final index = _employees.indexWhere((e) => e.id == id);
    if (index == -1) throw Exception('Employee not found');
    final updated = employee.copyWith(id: id);
    _employees[index] = updated;
    return updated;
  }

  /// Delete employee
  Future<bool> deleteEmployee(int id) async {
    await _delay();
    final index = _employees.indexWhere((e) => e.id == id);
    if (index == -1) return false;
    _employees.removeAt(index);
    return true;
  }
}
