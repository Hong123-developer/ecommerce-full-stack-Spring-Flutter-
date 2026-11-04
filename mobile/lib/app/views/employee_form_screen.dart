import 'package:flutter/material.dart';
import 'package:get/get.dart';
import '../controllers/employee_controller.dart';
import '../data/repositories/employee_repository.dart';

/// Employee Form Screen - សម្រាប់បង្កើតថ្មី (Add) និងកែប្រែ (Update) ព័ត៌មានបុគ្គលិក
class EmployeeFormScreen extends StatelessWidget {
  const EmployeeFormScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final controller = Get.isRegistered<EmployeeController>()
        ? Get.find<EmployeeController>()
        : Get.put(EmployeeController(Get.find<EmployeeRepository>()));

    final isEdit = controller.selectedEmployee.value != null;
    final currentId = controller.selectedEmployee.value?.id;

    final departments = ['IT', 'Design', 'HR', 'Marketing', 'Finance', 'Operations', 'Sales'];

    return Scaffold(
      backgroundColor: const Color(0xFFF8FAFC),
      appBar: AppBar(
        title: Text(
          isEdit ? 'កែប្រែព័ត៌មានបុគ្គលិក' : 'បន្ថែមបុគ្គលិកថ្មី',
          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
        ),
        backgroundColor: const Color(0xFF4F46E5),
        foregroundColor: Colors.white,
        elevation: 0,
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(20),
        child: Form(
          key: controller.formKey,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // === Header Card Info ===
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: isEdit ? const Color(0xFFEFF6FF) : const Color(0xFFF0FDF4),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: isEdit ? const Color(0xFFBFDBFE) : const Color(0xFFBBF7D0),
                  ),
                ),
                child: Row(
                  children: [
                    Icon(
                      isEdit ? Icons.edit_note_rounded : Icons.person_add_rounded,
                      color: isEdit ? const Color(0xFF2563EB) : const Color(0xFF16A34A),
                      size: 28,
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            isEdit
                                ? 'ទម្រង់កែប្រែព័ត៌មានបុគ្គលិក ID: #$currentId'
                                : 'ទម្រង់ចុះឈ្មោះបុគ្គលិកថ្មី',
                            style: TextStyle(
                              fontWeight: FontWeight.bold,
                              fontSize: 14,
                              color: isEdit ? const Color(0xFF1E40AF) : const Color(0xFF166534),
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'សូមបំពេញព័ត៌មានចាំបាច់ (*) ឱ្យបានត្រឹមត្រូវ',
                            style: TextStyle(
                              fontSize: 12,
                              color: isEdit ? const Color(0xFF3B82F6) : const Color(0xFF15803D),
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 20),

              // === Input Fields Card ===
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: Colors.white,
                  borderRadius: BorderRadius.circular(20),
                  border: Border.all(color: Colors.grey.shade100),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withOpacity(0.04),
                      blurRadius: 10,
                      offset: const Offset(0, 3),
                    ),
                  ],
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // 1. Employee Name
                    _buildLabel('ឈ្មោះពេញ (Full Name) *'),
                    TextFormField(
                      controller: controller.nameController,
                      validator: (v) => controller.validateRequired(v, 'ឈ្មោះបុគ្គលិក'),
                      decoration: _buildInputDecoration(
                        hintText: 'ឧទាហរណ៍៖ សុខា ចន្ថា',
                        icon: Icons.person_outline_rounded,
                      ),
                    ),
                    const SizedBox(height: 16),

                    // 2. Position
                    _buildLabel('មុខតំណែង (Position) *'),
                    TextFormField(
                      controller: controller.positionController,
                      validator: (v) => controller.validateRequired(v, 'មុខតំណែង'),
                      decoration: _buildInputDecoration(
                        hintText: 'ឧទាហរណ៍៖ Software Developer',
                        icon: Icons.work_outline_rounded,
                      ),
                    ),
                    const SizedBox(height: 16),

                    // 3. Department
                    _buildLabel('ផ្នែក / ដេប៉ាតឺម៉ង់ (Department) *'),
                    TextFormField(
                      controller: controller.departmentController,
                      validator: (v) => controller.validateRequired(v, 'ផ្នែក/ដេប៉ាតឺម៉ង់'),
                      decoration: _buildInputDecoration(
                        hintText: 'ឧទាហរណ៍៖ IT, HR, Marketing',
                        icon: Icons.business_rounded,
                      ),
                    ),
                    const SizedBox(height: 6),
                    // Quick select department chips
                    Wrap(
                      spacing: 6,
                      runSpacing: 4,
                      children: departments.map((d) {
                        return ActionChip(
                          label: Text(d, style: const TextStyle(fontSize: 11)),
                          backgroundColor: const Color(0xFFF1F5F9),
                          onPressed: () {
                            controller.departmentController.text = d;
                          },
                        );
                      }).toList(),
                    ),
                    const SizedBox(height: 16),

                    // 4. Salary
                    _buildLabel('ប្រាក់ខែ (\$ Salary) *'),
                    TextFormField(
                      controller: controller.salaryController,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      validator: controller.validateSalary,
                      decoration: _buildInputDecoration(
                        hintText: 'ឧទាហរណ៍៖ 1200',
                        icon: Icons.attach_money_rounded,
                      ),
                    ),
                    const SizedBox(height: 16),

                    // 5. Phone
                    _buildLabel('លេខទូរស័ព្ទ (Phone Number)'),
                    TextFormField(
                      controller: controller.phoneController,
                      keyboardType: TextInputType.phone,
                      decoration: _buildInputDecoration(
                        hintText: 'ឧទាហរណ៍៖ 012 345 678',
                        icon: Icons.phone_outlined,
                      ),
                    ),
                    const SizedBox(height: 16),

                    // 6. Email
                    _buildLabel('អ៊ីមែល (Email)'),
                    TextFormField(
                      controller: controller.emailController,
                      keyboardType: TextInputType.emailAddress,
                      validator: controller.validateEmail,
                      decoration: _buildInputDecoration(
                        hintText: 'ឧទាហរណ៍៖ employee@company.com',
                        icon: Icons.email_outlined,
                      ),
                    ),
                  ],
                ),
              ),

              const SizedBox(height: 24),

              // === Submit Buttons ===
              Obx(() => SizedBox(
                width: double.infinity,
                height: 52,
                child: ElevatedButton(
                  onPressed: controller.isSaving.value
                      ? null
                      : () {
                          if (isEdit && currentId != null) {
                            controller.updateEmployee(currentId);
                          } else {
                            controller.createEmployee();
                          }
                        },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF4F46E5),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 2,
                  ),
                  child: controller.isSaving.value
                      ? const Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            SizedBox(
                              width: 20,
                              height: 20,
                              child: CircularProgressIndicator(color: Colors.white, strokeWidth: 2.2),
                            ),
                            SizedBox(width: 12),
                            Text('កំពុងរក្សាទុក...'),
                          ],
                        )
                      : Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(isEdit ? Icons.save_rounded : Icons.check_circle_rounded, size: 20),
                            const SizedBox(width: 8),
                            Text(
                              isEdit ? 'រក្សាទុកការកែប្រែ' : 'បញ្ចូលបុគ្គលិកថ្មី',
                              style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                            ),
                          ],
                        ),
                ),
              )),

              const SizedBox(height: 12),

              // Cancel Button
              SizedBox(
                width: double.infinity,
                height: 48,
                child: OutlinedButton(
                  onPressed: () {
                    controller.clearForm();
                    Get.back();
                  },
                  style: OutlinedButton.styleFrom(
                    side: BorderSide(color: Colors.grey.shade300),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                  ),
                  child: const Text(
                    'បោះបង់',
                    style: TextStyle(
                      fontSize: 15,
                      color: Color(0xFF64748B),
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 32),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildLabel(String text) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Text(
        text,
        style: const TextStyle(
          fontSize: 13,
          fontWeight: FontWeight.w600,
          color: Color(0xFF334155),
        ),
      ),
    );
  }

  InputDecoration _buildInputDecoration({
    required String hintText,
    required IconData icon,
  }) {
    return InputDecoration(
      hintText: hintText,
      hintStyle: TextStyle(color: Colors.grey.shade400, fontSize: 13),
      prefixIcon: Icon(icon, size: 20, color: const Color(0xFF64748B)),
      filled: true,
      fillColor: const Color(0xFFF8FAFC),
      contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      border: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide(color: Colors.grey.shade200),
      ),
      enabledBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: BorderSide(color: Colors.grey.shade200),
      ),
      focusedBorder: OutlineInputBorder(
        borderRadius: BorderRadius.circular(12),
        borderSide: const BorderSide(color: Color(0xFF4F46E5), width: 2),
      ),
    );
  }
}

