import 'package:flutter/material.dart';
import 'package:get/get.dart';
import '../data/models/employee_model.dart';
import '../data/repositories/employee_repository.dart';

/// Employee Controller - គ្រប់គ្រង state សម្រាប់ Employee CRUD
class EmployeeController extends GetxController {
  final EmployeeRepository _employeeRepository;

  EmployeeController(this._employeeRepository);

  // === Reactive State ===
  final employees = <EmployeeModel>[].obs;   // បញ្ជីបុគ្គលិក
  final isLoading = false.obs;                // កំពុង loading
  final isSaving = false.obs;                 // កំពុង save
  final errorMessage = ''.obs;                // error message
  final selectedEmployee = Rx<EmployeeModel?>(null);

  // === Form Controllers ===
  final nameController = TextEditingController();
  final positionController = TextEditingController();
  final departmentController = TextEditingController();
  final salaryController = TextEditingController();
  final phoneController = TextEditingController();
  final emailController = TextEditingController();
  final formKey = GlobalKey<FormState>();

  @override
  void onInit() {
    super.onInit();
    fetchEmployees();
  }

  @override
  void onClose() {
    nameController.dispose();
    positionController.dispose();
    departmentController.dispose();
    salaryController.dispose();
    phoneController.dispose();
    emailController.dispose();
    super.onClose();
  }

  /// ទាញយកបញ្ជីបុគ្គលិកទាំងអស់
  Future<void> fetchEmployees() async {
    try {
      isLoading.value = true;
      errorMessage.value = '';
      final result = await _employeeRepository.getEmployees();
      employees.value = result;
    } catch (e) {
      errorMessage.value = 'មិនអាចទាញយកបញ្ជីបុគ្គលិកបានទេ';
    } finally {
      isLoading.value = false;
    }
  }

  /// បង្កើតបុគ្គលិកថ្មី
  Future<void> createEmployee() async {
    if (!formKey.currentState!.validate()) return;

    try {
      isSaving.value = true;
      errorMessage.value = '';

      final employee = EmployeeModel(
        name: nameController.text.trim(),
        position: positionController.text.trim(),
        department: departmentController.text.trim(),
        salary: double.tryParse(salaryController.text) ?? 0,
        phone: phoneController.text.trim(),
        email: emailController.text.trim(),
      );

      await _employeeRepository.createEmployee(employee);
      await fetchEmployees();
      clearForm();
      Get.back();

      Get.snackbar(
        'ជោគជ័យ',
        'បុគ្គលិកថ្មីត្រូវបានបន្ថែមដោយជោគជ័យ!',
        snackPosition: SnackPosition.BOTTOM,
        backgroundColor: Colors.green,
        colorText: Colors.white,
        margin: const EdgeInsets.all(16),
      );
    } catch (e) {
      errorMessage.value = 'មិនអាចបន្ថែមបុគ្គលិកបានទេ';
      Get.snackbar(
        'បរាជ័យ',
        errorMessage.value,
        snackPosition: SnackPosition.BOTTOM,
        backgroundColor: Colors.red,
        colorText: Colors.white,
        margin: const EdgeInsets.all(16),
      );
    } finally {
      isSaving.value = false;
    }
  }

  /// កែប្រែព័ត៌មានបុគ្គលិក
  Future<void> updateEmployee(int id) async {
    if (!formKey.currentState!.validate()) return;

    try {
      isSaving.value = true;
      errorMessage.value = '';

      final employee = EmployeeModel(
        id: id,
        name: nameController.text.trim(),
        position: positionController.text.trim(),
        department: departmentController.text.trim(),
        salary: double.tryParse(salaryController.text) ?? 0,
        phone: phoneController.text.trim(),
        email: emailController.text.trim(),
      );

      await _employeeRepository.updateEmployee(id, employee);
      await fetchEmployees();
      clearForm();
      Get.back();

      Get.snackbar(
        'ជោគជ័យ',
        'ព័ត៌មានបុគ្គលិកត្រូវបានកែប្រែដោយជោគជ័យ!',
        snackPosition: SnackPosition.BOTTOM,
        backgroundColor: Colors.green,
        colorText: Colors.white,
        margin: const EdgeInsets.all(16),
      );
    } catch (e) {
      errorMessage.value = 'មិនអាចកែប្រែព័ត៌មានបុគ្គលិកបានទេ';
      Get.snackbar(
        'បរាជ័យ',
        errorMessage.value,
        snackPosition: SnackPosition.BOTTOM,
        backgroundColor: Colors.red,
        colorText: Colors.white,
        margin: const EdgeInsets.all(16),
      );
    } finally {
      isSaving.value = false;
    }
  }

  /// លុបបុគ្គលិកចេញពីប្រព័ន្ធ
  Future<void> deleteEmployee(int id) async {
    try {
      isLoading.value = true;
      await _employeeRepository.deleteEmployee(id);
      await fetchEmployees();

      Get.snackbar(
        'ជោគជ័យ',
        'បុគ្គលិកត្រូវបានលុបដោយជោគជ័យ!',
        snackPosition: SnackPosition.BOTTOM,
        backgroundColor: Colors.green,
        colorText: Colors.white,
        margin: const EdgeInsets.all(16),
      );
    } catch (e) {
      Get.snackbar(
        'បរាជ័យ',
        'មិនអាចលុបបុគ្គលិកបានទេ: $e',
        snackPosition: SnackPosition.BOTTOM,
        backgroundColor: Colors.red,
        colorText: Colors.white,
        margin: const EdgeInsets.all(16),
      );
    } finally {
      isLoading.value = false;
    }
  }

  /// Fill form for editing
  void fillFormForEdit(EmployeeModel employee) {
    nameController.text = employee.name;
    positionController.text = employee.position;
    departmentController.text = employee.department;
    salaryController.text = employee.salary.toString();
    phoneController.text = employee.phone ?? '';
    emailController.text = employee.email ?? '';
    selectedEmployee.value = employee;
  }

  /// Clear form
  void clearForm() {
    nameController.clear();
    positionController.clear();
    departmentController.clear();
    salaryController.clear();
    phoneController.clear();
    emailController.clear();
    selectedEmployee.value = null;
  }

  /// Validate required field
  String? validateRequired(String? value, String fieldName) {
    if (value == null || value.trim().isEmpty) {
      return 'សូមបញ្ចូល $fieldName';
    }
    return null;
  }

  /// Validate salary
  String? validateSalary(String? value) {
    if (value == null || value.isEmpty) {
      return 'សូមបញ្ចូលប្រាក់ខែ';
    }
    final salary = double.tryParse(value);
    if (salary == null || salary <= 0) {
      return 'ប្រាក់ខែត្រូវជាចំនួនវិជ្ជមាន';
    }
    return null;
  }

  /// Validate email
  String? validateEmail(String? value) {
    if (value != null && value.isNotEmpty && !GetUtils.isEmail(value)) {
      return 'Email មិនត្រឹមត្រូវ';
    }
    return null;
  }
}
