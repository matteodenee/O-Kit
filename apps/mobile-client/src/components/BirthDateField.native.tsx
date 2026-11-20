import DateTimePicker, {
  DateTimePickerEvent,
} from '@react-native-community/datetimepicker';
import { useState } from 'react';
import { Platform, Pressable, StyleSheet, Text, View } from 'react-native';

type BirthDateFieldProps = {
  onChange: (value: string | null) => void;
  value: string | null;
};

const today = startOfDay(new Date());

export function BirthDateField({ onChange, value }: BirthDateFieldProps) {
  const [isPickerVisible, setPickerVisible] = useState(false);
  const selectedDate = value ? parseLocalDate(value) : defaultPickerDate();

  function handleChange(event: DateTimePickerEvent, date?: Date) {
    if (Platform.OS === 'android') {
      setPickerVisible(false);
    }

    if (event.type === 'set' && date) {
      onChange(formatApiDate(date));
    }
  }

  return (
    <View style={styles.field}>
      <Text style={styles.label}>Date de naissance</Text>
      <Pressable
        accessibilityHint="Ouvre un calendrier"
        accessibilityRole="button"
        onPress={() => setPickerVisible(true)}
        style={({ pressed }) => [styles.input, pressed && styles.pressed]}
      >
        <Text style={value ? styles.value : styles.placeholder}>
          {value ? formatDisplayDate(selectedDate) : 'Choisir une date'}
        </Text>
        <Text aria-hidden style={styles.calendarIcon}>
          📅
        </Text>
      </Pressable>

      {isPickerVisible ? (
        <View style={styles.pickerContainer}>
          <DateTimePicker
            display={Platform.OS === 'ios' ? 'inline' : 'calendar'}
            maximumDate={today}
            mode="date"
            onChange={handleChange}
            value={selectedDate}
          />
          {Platform.OS === 'ios' ? (
            <Pressable
              accessibilityRole="button"
              onPress={() => setPickerVisible(false)}
              style={styles.doneButton}
            >
              <Text style={styles.doneButtonText}>Valider la date</Text>
            </Pressable>
          ) : null}
        </View>
      ) : null}

      {value ? (
        <Pressable accessibilityRole="button" onPress={() => onChange(null)}>
          <Text style={styles.clearText}>Effacer la date</Text>
        </Pressable>
      ) : null}
    </View>
  );
}

function defaultPickerDate() {
  const date = new Date(today);
  date.setFullYear(date.getFullYear() - 18);
  return date;
}

function formatApiDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDisplayDate(date: Date) {
  return new Intl.DateTimeFormat('fr-FR').format(date);
}

function parseLocalDate(value: string) {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

const styles = StyleSheet.create({
  calendarIcon: {
    fontSize: 18,
  },
  clearText: {
    alignSelf: 'flex-start',
    color: '#b75328',
    fontSize: 12,
    fontWeight: '700',
    marginTop: 2,
  },
  doneButton: {
    alignItems: 'center',
    backgroundColor: '#c95f2b',
    borderRadius: 10,
    marginTop: 8,
    padding: 12,
  },
  doneButtonText: {
    color: '#fffaf1',
    fontSize: 14,
    fontWeight: '800',
  },
  field: {
    gap: 7,
  },
  input: {
    alignItems: 'center',
    backgroundColor: '#f5efe4',
    borderColor: '#ded3c2',
    borderRadius: 12,
    borderWidth: 1,
    flexDirection: 'row',
    justifyContent: 'space-between',
    minHeight: 49,
    paddingHorizontal: 15,
  },
  label: {
    color: '#344b45',
    fontSize: 12,
    fontWeight: '800',
  },
  pickerContainer: {
    overflow: 'hidden',
  },
  placeholder: {
    color: '#91897d',
    fontSize: 15,
  },
  pressed: {
    opacity: 0.75,
  },
  value: {
    color: '#17322c',
    fontSize: 15,
  },
});
