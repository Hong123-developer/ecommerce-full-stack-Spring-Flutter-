import '../models/employee_model.dart';
import '../providers/employee_provider.dart';

/// Employee Repository - Repository Pattern សម្រាប់ Employees
/// គ្រប់គ្រង business logic រវាង Controller និង Provider
class EmployeeRepository {
  final EmployeeProvider _employeeProvider;

  EmployeeRepository(this._employeeProvider);

  /// ទាញយកបញ្ជីបុគ្គលិកទាំងអស់
  Future<List<EmployeeModel>> getEmployees() async {
    try {
      return await _employeeProvider.getEmployees();
    } catch (e) {
      rethrow;
    }
  }

  /// ទាញយកព័ត៌មានបុគ្គលិកតាម ID
  Future<EmployeeModel?> getEmployeeById(int id) async {
    try {
      return await _employeeProvider.getEmployeeById(id);
    } catch (e) {
      rethrow;
    }
  }

  /// បង្កើតបុគ្គលិកថ្មី
  Future<EmployeeModel> createEmployee(EmployeeModel employee) async {
    try {
      return await _employeeProvider.createEmployee(employee);
    } catch (e) {
      rethrow;
    }
  }

  /// កែប្រែព័ត៌មានបុគ្គលិក
  Future<EmployeeModel> updateEmployee(int id, EmployeeModel employee) async {
    try {
      return await _employeeProvider.updateEmployee(id, employee);
    } catch (e) {
      rethrow;
    }
  }

  /// លុបបុគ្គលិក
  Future<bool> deleteEmployee(int id) async {
    try {
      return await _employeeProvider.deleteEmployee(id);
    } catch (e) {
      rethrow;
    }
  }
}
