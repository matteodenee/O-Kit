import { ChangeEvent } from 'react';
import { StyleSheet, Text, View } from 'react-native';

type BirthDateFieldProps = {
  onChange: (value: string | null) => void;
  value: string | null;
};

const maximumDate = formatApiDate(new Date());

export function BirthDateField({ onChange, value }: BirthDateFieldProps) {
  function handleChange(event: ChangeEvent<HTMLInputElement>) {
    onChange(event.currentTarget.value || null);
  }

  return (
    <View style={styles.field}>
      <Text style={styles.label}>Date de naissance</Text>
      <input
        aria-label="Date de naissance"
        max={maximumDate}
        onChange={handleChange}
        style={inputStyle}
        type="date"
        value={value ?? ''}
      />
    </View>
  );
}

function formatApiDate(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

const inputStyle = {
  backgroundColor: '#f5efe4',
  border: '1px solid #ded3c2',
  borderRadius: 12,
  boxSizing: 'border-box' as const,
  color: '#17322c',
  fontFamily: 'inherit',
  fontSize: 15,
  minHeight: 49,
  padding: '0 15px',
  width: '100%',
};

const styles = StyleSheet.create({
  field: {
    gap: 7,
  },
  label: {
    color: '#344b45',
    fontSize: 12,
    fontWeight: '800',
  },
});
